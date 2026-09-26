package llc.feelingfroggy.finances.api;

import java.time.Instant;
import java.util.List;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.DomainRuleViolation;
import llc.feelingfroggy.finances.domain.ImportBatch;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.service.ImportService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Statement upload (M2). CSV, OFX and QFX; PDF follows. */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    /**
     * Files this large are not real statements. Bounded here as well as by the servlet limit so
     * the rejection is a clear message rather than a container-level error, and so a huge upload
     * cannot be streamed to the parser before anything checks it.
     */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final ImportService imports;
    private final AccountRepository accounts;
    private final CurrentUser currentUser;

    public ImportController(ImportService imports, AccountRepository accounts,
                            CurrentUser currentUser) {
        this.imports = imports;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    /**
     * Uploads a statement and applies it to an account.
     *
     * <p>Safe to repeat: rows already present are counted as duplicates and skipped, so running the
     * same file twice is a no-op rather than a doubling.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult upload(@RequestParam(value = "accountId", required = false) Long accountId,
                               @RequestParam("file") MultipartFile file) {
        // The parser is chosen from the file's extension inside ImportService.
        Long userId = currentUser.id();

        // Optional, because some exports name an account on every row — a brokerage history covers
        // the whole portfolio. For those the nominated account is ignored; for a single-account
        // statement it is required, since nothing else identifies where the rows belong.
        Account account = null;
        if (accountId != null) {
            account = accounts.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown account"));
        }

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "Statements are limited to 10 MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The upload could not be read");
        }

        try {
            var outcome = imports.importStatement(userId, account, content,
                file.getOriginalFilename());
            return ImportResult.of(outcome.batch(), outcome.unlinked());
        } catch (DomainRuleViolation | AiServiceClient.AiServiceException e) {
            // The batch row is already marked failed with the reason. Both of these carry a
            // sentence written for a person, so it is returned: an unparseable file is a normal
            // outcome, not a server fault.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (RuntimeException e) {
            // Anything else is a fault whose message may describe the schema or quote the file.
            // The batch has the sanitized reason; the client gets the same sentence.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "The import failed before any rows were saved.");
        }
    }

    /**
     * Uploads a brokerage positions export as a holdings snapshot.
     *
     * <p>A separate endpoint rather than a flag on the one above, because the two files mean
     * different things: a statement records money moving, a positions file records what is owned
     * at a moment. Nothing here reaches the ledger. See ImportService#importPositions.
     *
     * <p>No {@code accountId} parameter — a positions file names an account on every row.
     */
    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult uploadPositions(@RequestParam("file") MultipartFile file) {
        Long userId = currentUser.id();

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "Statements are limited to 10 MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The upload could not be read");
        }

        try {
            var outcome = imports.importPositions(userId, content, file.getOriginalFilename());
            return ImportResult.of(outcome.batch(), outcome.unlinked());
        } catch (DomainRuleViolation | AiServiceClient.AiServiceException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "The import failed before any rows were saved.");
        }
    }

    @GetMapping
    public List<ImportResult> recent() {
        return imports.recent(currentUser.id()).stream()
            .map(batch -> ImportResult.of(batch, List.of()))
            .toList();
    }

    /**
     * @param appliedCount rows newly added
     * @param duplicateCount rows already present, skipped — a re-import is mostly these
     */
    public record ImportResult(Long id, Long accountId, String filename, String status,
                               int rowCount, int appliedCount, int duplicateCount,
                               String error, Instant startedAt, Instant completedAt,
                               /**
                                * Accounts this file refers to that do not exist here yet, with the
                                * institution's own name and the stable key to link them by. The
                                * client offers to create these rather than asking a person to look
                                * up and type their own account digits.
                                */
                               List<UnlinkedAccountView> unlinkedAccounts,
                               /**
                                * What the parser could not read or had to assume — a row with an
                                * unreadable date, a file that was not UTF-8, a checkpoint it would
                                * not record. Each one is a sentence for the person who uploaded
                                * the file; an import that lost rows must not look like one that
                                * did not.
                                */
                               List<String> warnings,
                               int autoCategorized, int suggested) {
        static ImportResult of(ImportBatch batch, List<ImportService.UnlinkedAccount> unlinked) {
            return new ImportResult(batch.getId(),
                batch.getAccount() == null ? null : batch.getAccount().getId(),
                batch.getOriginalFilename(), batch.getStatus().code(), batch.getRowCount(),
                batch.getAppliedCount(), batch.getDuplicateCount(), batch.getError(),
                batch.getStartedAt(), batch.getCompletedAt(),
                unlinked.stream().map(UnlinkedAccountView::of).toList(),
                batch.getWarnings(),
                batch.getAutoCategorized(), batch.getSuggested());
        }
    }

    public record UnlinkedAccountView(String key, String mask, String name, int transactionCount) {
        static UnlinkedAccountView of(ImportService.UnlinkedAccount source) {
            return new UnlinkedAccountView(source.key(), source.mask(), source.name(),
                source.transactionCount());
        }
    }
}
