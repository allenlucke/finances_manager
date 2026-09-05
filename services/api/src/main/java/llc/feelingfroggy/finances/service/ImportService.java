package llc.feelingfroggy.finances.service;

import java.util.List;
import java.util.Optional;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.ParsedPosition;
import llc.feelingfroggy.finances.ai.ParsedTransaction;
import llc.feelingfroggy.finances.ai.ParseResult;
import llc.feelingfroggy.finances.ai.PositionsResult;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.Direction;
import llc.feelingfroggy.finances.domain.Holding;
import llc.feelingfroggy.finances.domain.ImportBatch;
import llc.feelingfroggy.finances.domain.ImportFormat;
import llc.feelingfroggy.finances.domain.Statement;
import llc.feelingfroggy.finances.domain.Transaction;
import llc.feelingfroggy.finances.domain.TxnSource;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.repo.HoldingRepository;
import llc.feelingfroggy.finances.repo.ImportBatchRepository;
import llc.feelingfroggy.finances.repo.SecurityRepository;
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
    private final SecurityRepository securities;
    private final HoldingRepository holdings;

    public ImportService(AiServiceClient aiService, TransactionRepository transactions,
                         AccountRepository accounts, ImportBatchRepository batches,
                         ImportBatchRecorder recorder, StatementRepository statements,
                         SecurityRepository securities, HoldingRepository holdings) {
        this.aiService = aiService;
        this.transactions = transactions;
        this.accounts = accounts;
        this.batches = batches;
        this.recorder = recorder;
        this.statements = statements;
        this.securities = securities;
        this.holdings = holdings;
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
    private Optional<Account> resolveAccount(Long userId, String accountKey, String accountMask) {
        Optional<Account> byKey = accounts.findByUserIdAndExternalId(userId, accountKey);
        if (byKey.isPresent()) {
            return byKey;
        }

        List<Account> byMask = accountMask == null
            ? List.of()
            : accounts.findByUserIdAndMask(userId, accountMask);
        if (byMask.size() != 1) {
            return Optional.empty();
        }

        Account matched = byMask.getFirst();
        if (matched.getExternalId() != null && !matched.getExternalId().equals(accountKey)) {
            // The one account with these digits is already linked to a *different* institution
            // account. This is a new account that happens to share a last four — a savings beside
            // a checking is the ordinary case — and filing its rows here would post one account's
            // money to another with nothing to say so. Unlinked, so it is reported and created
            // rather than guessed. The guard on size() above only covers ambiguity among existing
            // rows; this covers the link already being spoken for.
            return Optional.empty();
        }
        if (matched.getExternalId() == null) {
            matched.setExternalId(accountKey);
            accounts.save(matched);
        }
        return Optional.of(matched);
    }

    @Transactional
    public ImportOutcome importStatement(Long userId, Account account, byte[] content,
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

        // Everything from here to the flush is one attempt. If any of it fails the batch is marked
        // failed with a reason — in its own transaction, so the record survives the rollback —
        // and nothing from the file is kept. Before this, only the parser call was guarded: a row
        // that violated a constraint rolled the whole import back and left the batch PENDING with
        // no error, which is the one state the recorder promises never to leave behind.
        try {
        for (var row : parsed.transactions()) {
            // A row that names its own account wins over whatever the caller nominated: a brokerage
            // history spans accounts, and applying all of it to one would be silently wrong.
            Account target = account;
            if (row.carriesAccount()) {
                var resolved = resolveAccount(userId, row.accountKey(), row.accountMask());
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

            // The API computes the identity; the parser's dedupe_key is not read. See
            // TransactionService.dedupeKey for why there is exactly one implementation.
            String dedupeKey = TransactionService.dedupeKey(target.getId(), row.transactionDate(),
                row.amount(), row.description(), row.externalId());

            // Includes soft-deleted rows on purpose — see the note above.
            if (transactions.findByAccountIdAndDedupeKey(target.getId(), dedupeKey).isPresent()) {
                duplicates++;
                continue;
            }

            var transaction = new Transaction(userId, target, row.transactionDate(), row.amount(),
                Direction.DEBIT.code().equals(row.direction()) ? Direction.DEBIT : Direction.CREDIT,
                row.description(), dedupeKey);
            transaction.setPostedDate(row.postedDate());
            transaction.setMerchant(row.merchant());
            transaction.setExternalId(row.externalId());
            transaction.setSource(TxnSource.FILE_IMPORT);
            transaction.setImportBatch(batch);

            if (row.isProbableTransfer()) {
                // The file itself said this is a payment rather than a purchase. Marking it as a
                // transfer keeps it out of spending totals — the budget was charged when the
                // purchase happened, and counting the payment too would double-charge it. The
                // other side is not known from one statement, so transferAccountId stays null
                // until M5 matches the pair.
                //
                // A refund (row.isProbableRefund) is deliberately NOT handled here. It arrives as
                // an ordinary credit and lands in the review queue, where it can be categorized
                // against what it refunds. Treating it as a transfer made it uncategorizable by
                // CHECK constraint, and the category it refunded stayed overcharged for good.
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

        // Force the inserts now. A unique-index or NOT NULL violation otherwise surfaces at
        // commit, after this method has returned — past the catch below, and after the batch had
        // already been committed as APPLIED in a transaction of its own.
        transactions.flush();
        } catch (RuntimeException e) {
            recorder.fail(batchId, reasonFor(e));
            throw e;
        }

        if (!unlinked.isEmpty()) {
            String names = unlinked.values().stream()
                .map(UnlinkedAccount::describe)
                .collect(java.util.stream.Collectors.joining(", "));
            batch.setError(unlinked.size() + " account(s) in this file are not set up yet: "
                + names + ". Nothing was guessed — create them and import again.");
        }
        // Completed in THIS transaction, so the APPLIED status and its counts commit with the
        // rows they describe — or roll back with them. The recorder's REQUIRES_NEW completion
        // committed first, so a commit failure left a batch claiming rows that did not exist.
        var completed = markApplied(batch, parsed.transactions().size(), applied, duplicates);

        // Counts only. docs/SECURITY.md keeps descriptions, amounts and account identifiers out of
        // the log.
        log.info("import batch={} rows={} applied={} duplicates={} warnings={}",
            batch.getId(), parsed.transactions().size(), applied, duplicates,
            parsed.warnings() == null ? 0 : parsed.warnings().size());

        return new ImportOutcome(completed, List.copyOf(unlinked.values()));
    }

    /**
     * Imports a brokerage positions export as a holdings snapshot.
     *
     * <p>Deliberately separate from {@link #importStatement}: a positions file contains no money
     * movements, so nothing here touches the ledger. Forcing a snapshot into the transaction model
     * would invent money movements that never happened — docs/DOMAIN.md is explicit about it.
     *
     * <p><strong>Idempotent by snapshot.</strong> The key is (account, security, as_of), so
     * re-importing the same file updates each position rather than adding a second copy, while a
     * file downloaded on a later date lands as a new snapshot and the old one is kept. That is what
     * gives a position any history at all — the file itself has none.
     *
     * <p>No account is nominated, because every row names its own. Rows whose account does not
     * exist here are collected and reported rather than filed against a guess.
     */
    @Transactional
    public ImportOutcome importPositions(Long userId, byte[] content, String filename) {
        Long batchId = recorder.begin(userId, null, ImportFormat.CSV, filename);
        var batch = batches.findById(batchId).orElseThrow();

        PositionsResult parsed;
        try {
            parsed = aiService.parsePositions(content, filename);
        } catch (RuntimeException e) {
            recorder.fail(batchId, e.getMessage());
            throw e;
        }

        if (parsed.asOf() == null) {
            // Without a date the snapshot cannot be keyed, and every later import would overwrite
            // this one instead of accumulating beside it. Refusing beats silently losing history.
            recorder.fail(batchId, "This file carries no 'as of' date, so the snapshot cannot be dated.");
            throw new IllegalStateException(
                "This file carries no 'as of' date, so the snapshot cannot be dated.");
        }

        int applied = 0;
        int updated = 0;
        var unlinked = new java.util.LinkedHashMap<String, UnlinkedAccount>();

        try {
        for (var row : parsed.positions()) {
            var resolved = resolveAccount(userId, row.accountKey(), row.accountMask());
            if (resolved.isEmpty()) {
                unlinked.merge(row.accountKey(),
                    new UnlinkedAccount(row.accountKey(), row.accountMask(), row.accountName(), 1),
                    UnlinkedAccount::plusOne);
                continue;
            }
            var account = resolved.get();
            var security = findOrCreateSecurity(userId, row);

            var existing = holdings.findSnapshot(account.getId(), security.getId(), parsed.asOf());
            var holding = existing.orElseGet(
                () -> new Holding(userId, account, security, parsed.asOf(), row.currentValue()));

            holding.setMarketValue(row.currentValue());
            holding.setQuantity(row.quantity());
            holding.setLastPrice(row.lastPrice());
            holding.setCostBasis(row.costBasisTotal());
            holding.setAverageCost(row.averageCostBasis());
            holding.setTotalGainLoss(row.totalGainLoss());
            holding.setImportBatch(batch);
            holdings.save(holding);

            if (existing.isPresent()) {
                updated++;
            } else {
                applied++;
            }
        }
        holdings.flush();
        } catch (RuntimeException e) {
            recorder.fail(batchId, reasonFor(e));
            throw e;
        }

        if (!unlinked.isEmpty()) {
            String names = unlinked.values().stream()
                .map(UnlinkedAccount::describe)
                .collect(java.util.stream.Collectors.joining(", "));
            batch.setError(unlinked.size() + " account(s) in this file are not set up yet: "
                + names + ". Nothing was guessed — create them and import again.");
        }
        var completed = markApplied(batch, parsed.positions().size(), applied, updated);

        // Counts only — docs/SECURITY.md keeps holdings, values and account identifiers out of the log.
        log.info("positions batch={} rows={} new={} updated={} asOf={}",
            batch.getId(), parsed.positions().size(), applied, updated, parsed.asOf());

        return new ImportOutcome(completed, List.copyOf(unlinked.values()));
    }

    /** Marks the batch applied in the caller's transaction, so status commits with the rows. */
    private ImportBatch markApplied(ImportBatch batch, int rowCount, int applied, int duplicates) {
        batch.setRowCount(rowCount);
        batch.setAppliedCount(applied);
        batch.setDuplicateCount(duplicates);
        batch.setStatus(llc.feelingfroggy.finances.domain.ImportStatus.APPLIED);
        batch.setCompletedAt(java.time.Instant.now());
        return batches.save(batch);
    }

    /**
     * What to record on a failed batch. The user reads this in import history.
     *
     * <p>Our own rule violations carry a sentence written for a person. Anything else — a
     * constraint name, a SQL fragment, a driver message — is logged and replaced, because
     * docs/SECURITY.md keeps schema detail and row content out of anything a client sees.
     */
    private static String reasonFor(RuntimeException e) {
        if (e instanceof IllegalStateException && e.getMessage() != null) {
            return e.getMessage();
        }
        if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
            log.warn("Import refused by a database constraint", e);
            return "A row in this file violated a data rule. Nothing from it was saved.";
        }
        log.warn("Import failed", e);
        return "The import failed before any rows were saved.";
    }

    /**
     * Finds the instrument, creating it the first time it is seen.
     *
     * <p>One row per symbol per user, shared across accounts, so "how much of this do I hold in
     * total" is answerable. The name is refreshed on later imports because an export can carry a
     * fuller description than the one that created the row.
     */
    private llc.feelingfroggy.finances.domain.Security findOrCreateSecurity(Long userId,
                                                                           ParsedPosition row) {
        var existing = securities.findByUserIdAndSymbol(userId, row.symbol());
        if (existing.isPresent()) {
            var security = existing.get();
            if (row.description() != null && !row.description().isBlank()) {
                security.setName(row.description());
            }
            return securities.save(security);
        }
        return securities.save(new llc.feelingfroggy.finances.domain.Security(
            userId, row.symbol(), row.description(), classify(row), row.isCash()));
    }

    /**
     * A coarse instrument type, from what the export actually tells us.
     *
     * <p>Deliberately shallow. A positions file does not say whether something is an ETF or an
     * index fund, and guessing from the ticker would be inventing data — {@code unknown} is honest
     * and can be corrected later, whereas a confident wrong classification would not be noticed.
     */
    private static String classify(ParsedPosition row) {
        if (row.isCash()) {
            // The parser decides cash by symbol; money-market sweeps and dollar lines both land here.
            return row.quantity() == null ? "cash" : "money_market";
        }
        return "unknown";
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
     * What an import did, and what it could not do.
     *
     * <p>Returned rather than parked on a {@code ThreadLocal} for the controller to collect
     * afterwards, which is how it used to work. That value was set and never removed, so on a
     * pooled request thread it outlived the request that wrote it — stale on the next read, and a
     * tenant leak the moment there is a second user. A return value has none of those properties.
     *
     * @param batch the completed batch row
     * @param unlinked accounts the file names that do not exist here; empty when everything landed
     */
    public record ImportOutcome(ImportBatch batch, List<UnlinkedAccount> unlinked) {
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
