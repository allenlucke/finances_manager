package llc.feelingfroggy.finances.service;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.ParsedTransaction;
import llc.feelingfroggy.finances.ai.ParseResult;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.Direction;
import llc.feelingfroggy.finances.domain.ImportBatch;
import llc.feelingfroggy.finances.domain.ImportFormat;
import llc.feelingfroggy.finances.domain.Statement;
import llc.feelingfroggy.finances.domain.Transaction;
import llc.feelingfroggy.finances.domain.TxnSource;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.repo.ImportBatchRepository;
import llc.feelingfroggy.finances.repo.StatementRepository;
import llc.feelingfroggy.finances.repo.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an uploaded statement into ledger rows.
 *
 * <p>The flow follows docs/ARCHITECTURE.md: this service stores the batch, the Python service
 * parses the bytes, and this service decides what the result means and what gets persisted. The
 * parser never writes to the database and its output is never applied unchecked.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final AiServiceClient aiService;
    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final ImportBatchRepository batches;
    private final ImportBatchRecorder recorder;
    private final StatementRepository statements;

    public ImportService(AiServiceClient aiService, TransactionRepository transactions,
                         AccountRepository accounts, ImportBatchRepository batches,
                         ImportBatchRecorder recorder, StatementRepository statements) {
        this.aiService = aiService;
        this.transactions = transactions;
        this.accounts = accounts;
        this.batches = batches;
        this.recorder = recorder;
        this.statements = statements;
    }

    /**
     * Chooses a parser from the filename.
     *
     * <p>Extension rather than content sniffing: OFX and QFX are the same grammar under different
     * names, and a CSV has no reliable magic bytes to sniff for anyway. An unknown extension is
     * treated as CSV, since that is what "export to spreadsheet" produces under a dozen names.
     */
    public static ImportFormat formatFor(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".ofx")) {
            return ImportFormat.OFX;
        }
        if (lower.endsWith(".qfx")) {
            return ImportFormat.QFX;
        }
        return ImportFormat.CSV;
    }

    /**
     * Imports a statement into an account, choosing the parser from the file's extension.
     *
     * <p><strong>Idempotent.</strong> Re-importing an overlapping statement adds only the rows that
     * are genuinely new. Duplicates are detected on the parser's {@code dedupe_key}, which the
     * database also enforces as a unique index — so a race between two concurrent imports fails at
     * the constraint rather than double-posting.
     *
     * <p>The check deliberately includes soft-deleted rows: if a row was imported and then deleted,
     * re-importing must not resurrect it. Deleting something was a decision.
     */
    /**
     * Resolves the account a row belongs to, for exports that cover several.
     *
     * <p>Two steps, and deliberately no third. First the stable key the parser derived from the
     * institution's account number, which is exact. Failing that, the masked last four — but only
     * when it identifies exactly one account, because two accounts can share those digits and
     * guessing files a child's brokerage activity against a joint account.
     *
     * <p>When a row matches by mask alone, the key is written onto the account so every later
     * import matches exactly. The link is learned once rather than re-guessed each time.
     */
    private Optional<Account> resolveAccount(Long userId, ParsedTransaction row) {
        Optional<Account> byKey = accounts.findByUserIdAndExternalId(userId, row.accountKey());
        if (byKey.isPresent()) {
            return byKey;
        }

        List<Account> byMask = row.accountMask() == null
            ? List.of()
            : accounts.findByUserIdAndMask(userId, row.accountMask());
        if (byMask.size() != 1) {
            return Optional.empty();
        }

        Account matched = byMask.getFirst();
        if (matched.getExternalId() == null) {
            matched.setExternalId(row.accountKey());
            accounts.save(matched);
        }
        return Optional.of(matched);
    }

    @Transactional
    public ImportBatch importStatement(Long userId, Account account, byte[] content,
                                       String filename) {
        ImportFormat format = formatFor(filename);

        // Committed in its own transaction so the record survives whatever happens next.
        Long batchId = recorder.begin(userId, account, format, filename);
        var batch = batches.findById(batchId).orElseThrow();

        ParseResult parsed;
        try {
            // An opaque handle the parser folds into its dedupe keys — it is not required to be
            // an account. For a file that names an account on every row the parser uses that
            // instead, so a placeholder here is correct rather than a fallback.
            String accountRef = account == null ? "multi" : String.valueOf(account.getId());
            parsed = format == ImportFormat.CSV
                ? aiService.parseCsv(content, filename, accountRef)
                : aiService.parseOfx(content, filename, accountRef);
        } catch (RuntimeException e) {
            recorder.fail(batchId, e.getMessage());
            throw e;
        }

        if (account == null && parsed.transactions().stream().noneMatch(ParsedTransaction::carriesAccount)) {
            // Nothing in the file says where these belong and the caller did not say either.
            // Failing is the only honest option; picking an account would be a guess about money.
            recorder.fail(batchId, "This file does not name an account, so one must be chosen.");
            throw new IllegalStateException("This file does not name an account, so one must be chosen.");
        }

        int applied = 0;
        int duplicates = 0;

        // Accounts the file names that this system does not have yet, keyed so each is reported
        // once with the institution's own name — enough for the caller to offer to create them
        // rather than asking a person to type account digits they would have to look up.
        var unlinked = new java.util.LinkedHashMap<String, UnlinkedAccount>();

        for (var row : parsed.transactions()) {
            // A row that names its own account wins over whatever the caller nominated: a brokerage
            // history spans accounts, and applying all of it to one would be silently wrong.
            Account target = account;
            if (row.carriesAccount()) {
                var resolved = resolveAccount(userId, row);
                if (resolved.isEmpty()) {
                    // Skipped rather than filed against a fallback account. An unimported row is
                    // visible and fixable; a row on the wrong account is neither.
                    unlinked.merge(row.accountKey(),
                        new UnlinkedAccount(row.accountKey(), row.accountMask(), row.accountName(), 1),
                        UnlinkedAccount::plusOne);
                    continue;
                }
                target = resolved.get();
            }
            if (target == null) {
                continue;
            }

            // Includes soft-deleted rows on purpose — see the note above.
            if (transactions.findByAccountIdAndDedupeKey(target.getId(), row.dedupeKey()).isPresent()) {
                duplicates++;
                continue;
            }

            var transaction = new Transaction(userId, target, row.transactionDate(), row.amount(),
                Direction.DEBIT.code().equals(row.direction()) ? Direction.DEBIT : Direction.CREDIT,
                row.description(), row.dedupeKey());
            transaction.setPostedDate(row.postedDate());
            transaction.setMerchant(row.merchant());
            transaction.setExternalId(row.externalId());
            transaction.setSource(TxnSource.FILE_IMPORT);
            transaction.setImportBatch(batch);

            if (row.isProbableTransfer()) {
                // The file itself said this is a payment, refund or adjustment rather than a
                // purchase. Marking it as a transfer keeps it out of spending totals — the budget
                // was charged when the purchase happened, and counting the payment too would
                // double-charge it. The other side is not known from one statement, so
                // transferAccountId stays null until M5 matches the pair.
                transaction.markAsTransfer(null);
            }

            transactions.save(transaction);
            applied++;
        }

        // A checkpoint only makes sense against one account. A multi-account export has no single
        // closing balance, so none is recorded.
        if (account != null && parsed.statement() != null
                && parsed.statement().closingBalance() != null) {
            recordStatement(userId, account, batch, parsed.statement());
        }

        if (!unlinked.isEmpty()) {
            String names = unlinked.values().stream()
                .map(UnlinkedAccount::describe)
                .collect(java.util.stream.Collectors.joining(", "));
            recorder.note(batchId, unlinked.size() + " account(s) in this file are not set up yet: "
                + names + ". Nothing was guessed — create them and import again.");
        }
        lastUnlinked.set(List.copyOf(unlinked.values()));

        var completed = recorder.complete(batchId, parsed.transactions().size(), applied, duplicates);

        // Counts only. docs/SECURITY.md keeps descriptions, amounts and account identifiers out of
        // the log.
        log.info("import batch={} rows={} applied={} duplicates={} warnings={}",
            batch.getId(), parsed.transactions().size(), applied, duplicates,
            parsed.warnings() == null ? 0 : parsed.warnings().size());

        return completed;
    }

    /**
     * Records the statement's closing balance as a reconciliation checkpoint.
     *
     * <p>Skipped when one already exists for the period: re-importing the same statement must not
     * create a second checkpoint, and the unique index on {@code (account_id, period_end)} would
     * reject it anyway — better to no-op than to fail an otherwise successful import.
     */
    private void recordStatement(Long userId, Account account, ImportBatch batch,
                                 ParseResult.StatementSummary summary) {
        if (summary.periodEnd() == null) {
            return;
        }
        boolean exists = statements.findByAccountIdOrderByPeriodEndDesc(account.getId()).stream()
            .anyMatch(existing -> existing.getPeriodEnd().equals(summary.periodEnd()));
        if (exists) {
            return;
        }

        var statement = new Statement(userId, account,
            summary.periodStart() == null ? summary.periodEnd() : summary.periodStart(),
            summary.periodEnd(), summary.closingBalance());
        statement.setImportBatch(batch);
        statements.save(statement);
    }

    /**
     * Accounts named by the most recent import that this system does not have.
     *
     * <p>Held per-request rather than returned through the batch, because a batch row records what
     * happened while this describes what to do next.
     */
    private final ThreadLocal<List<UnlinkedAccount>> lastUnlinked =
        ThreadLocal.withInitial(List::of);

    public List<UnlinkedAccount> unlinkedFromLastImport() {
        return lastUnlinked.get();
    }

    /**
     * An account a file refers to that does not exist here yet.
     *
     * @param key the parser's one-way hash of the account number — the stable link, stored on the
     *     account so later imports match without anyone handling the number
     * @param mask last four digits, for a human to recognise it by
     * @param name the institution's own name for it
     */
    public record UnlinkedAccount(String key, String mask, String name, int transactionCount) {
        UnlinkedAccount plusOne(UnlinkedAccount ignored) {
            return new UnlinkedAccount(key, mask, name, transactionCount + 1);
        }

        String describe() {
            return (name == null || name.isBlank() ? "account" : name)
                + " (ending " + (mask == null ? "?" : mask) + ")";
        }
    }

    public List<ImportBatch> recent(Long userId) {
        return batches.findByUserIdOrderByStartedAtDesc(userId);
    }
}
