package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The single ledger primitive, replacing the legacy {@code expenseItem} + {@code incomeItem} —
 * which were the same shape with an opposite sign, and which every legacy report had to union.
 *
 * <p><strong>Sign.</strong> {@code amount} is always a positive magnitude and {@link Direction}
 * carries the sign, for every account type including credit cards. This mirrors the AI service's
 * {@code ParsedTransaction} exactly; the two are one wire contract and must change together.
 *
 * <p><strong>Dates are {@link LocalDate}, not {@link Instant}.</strong> A purchase "on the 14th" is
 * the 14th in the institution's reporting. Pushing it through a timezone would move it.
 *
 * <p><strong>Transfers are single-sided.</strong> One row is one account's view of a money
 * movement, because that is what a statement reports. Importing both sides yields two rows that
 * net to zero, so net worth stays right without double-entry bookkeeping.
 */
@Entity
@Table(name = "transaction")
public class Transaction extends UserOwned {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /**
     * Null means "inherit the account's entity". Set only to override — the case that matters at
     * tax time is a personal card carrying a business expense, which provenance cannot resolve.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_entity_id")
    private LedgerEntity ledgerEntity;

    /** Null means uncategorized: awaiting review, or a transfer (which must never be categorized). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "posted_date")
    private LocalDate postedDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Convert(converter = Direction.Conv.class)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "merchant")
    private String merchant;

    @Column(name = "is_transfer", nullable = false)
    private boolean transfer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_account_id")
    private Account transferAccount;

    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    @Convert(converter = TxnSource.Conv.class)
    @Column(name = "source", nullable = false, length = 20)
    private TxnSource source = TxnSource.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private SourceConnection connection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "pending", nullable = false)
    private boolean pending;

    @Column(name = "pending_external_id", length = 255)
    private String pendingExternalId;

    @Column(name = "dedupe_key", nullable = false, length = 64)
    private String dedupeKey;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Transaction() {
    }

    public Transaction(Long userId, Account account, LocalDate transactionDate, BigDecimal amount,
                       Direction direction, String description, String dedupeKey) {
        super(userId);
        this.account = account;
        this.transactionDate = transactionDate;
        this.amount = amount;
        this.direction = direction;
        this.description = description;
        this.dedupeKey = dedupeKey;
    }

    /**
     * The amount's contribution to a balance: negative for a debit, positive for a credit.
     *
     * <p>The one place the sign convention is expressed in Java. Reporting sums are computed in SQL
     * (see the balance views), so this exists for single-row arithmetic and for tests that assert
     * the two agree.
     */
    public BigDecimal signedAmount() {
        return direction == Direction.DEBIT ? amount.negate() : amount;
    }

    /**
     * Marks this row as a payment or transfer between the user's own accounts, which is
     * <em>not</em> an expense.
     *
     * <p>This is the credit-card double-count rule, the subtlest piece of business logic carried
     * over from 2021: the budget was already charged when the purchase happened, so categorizing
     * the payment as well would count the spend twice. Clearing the category here keeps the
     * in-memory object consistent with the database CHECK that enforces the same thing.
     */
    public void markAsTransfer(Account otherSide) {
        this.transfer = true;
        this.category = null;
        this.transferAccount = otherSide;
    }

    /** Soft delete: the dedupe key stays claimed so a re-import cannot resurrect this row. */
    public void softDelete(Instant when) {
        this.deletedAt = when;
    }

    /**
     * Brings a soft-deleted row back.
     *
     * <p>The counterpart to {@link #softDelete}, and the reason deleting is safe to expose to
     * automation at all (D-17): a delete that can be undone is a far smaller thing to get wrong
     * than one that cannot. Nothing else changes — the dedupe key was never released, so the row
     * returns as itself rather than as a duplicate of whatever has been imported since.
     */
    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Account getAccount() {
        return account;
    }

    public LedgerEntity getLedgerEntity() {
        return ledgerEntity;
    }

    public void setLedgerEntity(LedgerEntity ledgerEntity) {
        this.ledgerEntity = ledgerEntity;
    }

    public Category getCategory() {
        return category;
    }

    /** Rejects the double-count rather than letting the database do it, so the error is readable. */
    public void setCategory(Category category) {
        if (category != null && transfer) {
            throw new IllegalStateException(
                "A transfer cannot be categorized: doing so would double-count the spend, "
                    + "because the budget was already charged when the purchase happened. "
                    + "See docs/DOMAIN.md.");
        }
        this.category = category;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(LocalDate postedDate) {
        this.postedDate = postedDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public boolean isTransfer() {
        return transfer;
    }

    public Account getTransferAccount() {
        return transferAccount;
    }

    public UUID getTransferGroupId() {
        return transferGroupId;
    }

    public void setTransferGroupId(UUID transferGroupId) {
        this.transferGroupId = transferGroupId;
    }

    public TxnSource getSource() {
        return source;
    }

    public void setSource(TxnSource source) {
        this.source = source;
    }

    public SourceConnection getConnection() {
        return connection;
    }

    public void setConnection(SourceConnection connection) {
        this.connection = connection;
    }

    public ImportBatch getImportBatch() {
        return importBatch;
    }

    public void setImportBatch(ImportBatch importBatch) {
        this.importBatch = importBatch;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public boolean isPending() {
        return pending;
    }

    public void setPending(boolean pending) {
        this.pending = pending;
    }

    public String getPendingExternalId() {
        return pendingExternalId;
    }

    public void setPendingExternalId(String pendingExternalId) {
        this.pendingExternalId = pendingExternalId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
