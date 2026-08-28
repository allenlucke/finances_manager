package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import llc.feelingfroggy.finances.domain.Direction;
import llc.feelingfroggy.finances.domain.Transaction;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.repo.CategoryRepository;
import llc.feelingfroggy.finances.repo.TransactionRepository;
import llc.feelingfroggy.finances.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionRepository transactions;
    private final TransactionService service;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final CurrentUser currentUser;

    public TransactionController(TransactionRepository transactions, TransactionService service,
                                 AccountRepository accounts, CategoryRepository categories,
                                 CurrentUser currentUser) {
        this.transactions = transactions;
        this.service = service;
        this.accounts = accounts;
        this.categories = categories;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Page<TransactionView> list(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;

        return transactions
            .findInRange(currentUser.id(), start, end, PageRequest.of(page, Math.min(size, 200)))
            .map(TransactionView::of);
    }

    /** Uncategorized, non-transfer rows awaiting a decision. Empty until M2/M3 produce any. */
    @GetMapping("/review")
    public Page<TransactionView> review(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return transactions
            .findNeedingReview(currentUser.id(), PageRequest.of(page, Math.min(size, 200)))
            .map(TransactionView::of);
    }

    /**
     * Manual entry — the only way transactions arrive until M2 lands file import.
     *
     * <p>A transfer writes <strong>both</strong> legs (see
     * {@link TransactionService#recordTransfer}), so paying a card actually reduces what the card
     * says you owe. Import stays single-sided, because there each statement supplies its own side.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<TransactionView> create(@Valid @RequestBody CreateTransaction request) {
        Long userId = currentUser.id();
        var account = accounts.findByIdAndUserId(request.accountId(), userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown account"));

        if (Boolean.TRUE.equals(request.transfer())) {
            if (request.transferAccountId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A transfer needs transferAccountId: both accounts are recorded");
            }
            var other = accounts.findByIdAndUserId(request.transferAccountId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown transfer account"));

            return service.recordTransfer(userId, account, other, request.transactionDate(),
                    request.amount(), request.direction(), request.description())
                .stream().map(TransactionView::of).toList();
        }

        var category = request.categoryId() == null ? null
            : categories.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown category"));

        return List.of(TransactionView.of(service.record(userId, account, request.transactionDate(),
            request.amount(), request.direction(), request.description(), category)));
    }

    /** Recategorize. Rejected for transfers by the domain, which the handler turns into a 422. */
    @PutMapping("/{id}/category")
    @Transactional
    public TransactionView categorize(@PathVariable Long id,
                                      @Valid @RequestBody Categorize request) {
        Long userId = currentUser.id();
        var transaction = transactions.findLive(id, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        transaction.setCategory(request.categoryId() == null ? null
            : categories.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown category")));

        return TransactionView.of(transactions.save(transaction));
    }

    /**
     * Soft delete. The row stays, so its dedupe key stays claimed and re-importing the same
     * statement cannot bring it back.
     *
     * <p>Deleting one leg of a transfer removes the other too: half a transfer would make money
     * appear to leave one account without arriving anywhere.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        var transaction = transactions.findLive(id, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        service.softDelete(transaction);
    }

    /**
     * What has been deleted, newest first — the recycle bin.
     *
     * <p>Every other endpoint here filters deleted rows out. This one exists because a delete you
     * cannot see is not really reversible, whatever the schema supports.
     */
    @GetMapping("/deleted")
    public List<TransactionView> deleted(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return transactions.findDeletedFor(currentUser.id(), PageRequest.of(page, Math.min(size, 500)))
            .map(TransactionView::of).getContent();
    }

    /** Undo a delete. Restores every leg removed by the same delete — see TransactionService. */
    @PostMapping("/{id}/restore")
    @Transactional
    public List<TransactionView> restore(@PathVariable Long id) {
        var transaction = transactions.findDeleted(id, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No deleted transaction with that id — it may already have been restored."));
        var groupId = transaction.getTransferGroupId();
        service.restore(transaction);

        // Report every row that came back, not just the one named, so a caller undoing a transfer
        // sees both legs return rather than assuming the other is still missing.
        if (groupId == null) {
            return List.of(TransactionView.of(transaction));
        }
        return transactions.findByTransferGroupId(groupId).stream()
            .filter(leg -> !leg.isDeleted())
            .map(TransactionView::of)
            .toList();
    }

    public record TransactionView(Long id, Long accountId, Long categoryId, LocalDate transactionDate,
                                  BigDecimal amount, String direction, BigDecimal signedAmount,
                                  String description, String merchant, boolean transfer,
                                  Long transferAccountId, String transferGroupId, String source) {
        static TransactionView of(Transaction t) {
            return new TransactionView(t.getId(), t.getAccount().getId(),
                t.getCategory() == null ? null : t.getCategory().getId(),
                t.getTransactionDate(), t.getAmount(), t.getDirection().code(), t.signedAmount(),
                t.getDescription(), t.getMerchant(), t.isTransfer(),
                t.getTransferAccount() == null ? null : t.getTransferAccount().getId(),
                t.getTransferGroupId() == null ? null : t.getTransferGroupId().toString(),
                t.getSource().code());
        }
    }

    public record CreateTransaction(
        @NotNull Long accountId,
        @NotNull LocalDate transactionDate,
        @NotNull @Positive BigDecimal amount,
        @NotNull Direction direction,
        @NotBlank String description,
        Long categoryId,
        Boolean transfer,
        Long transferAccountId) {
    }

    public record Categorize(Long categoryId) {
    }
}
