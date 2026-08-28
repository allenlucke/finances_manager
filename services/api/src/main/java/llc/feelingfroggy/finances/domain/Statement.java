package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A reconciliation checkpoint: what the institution says the balance was on a given date.
 *
 * <p>Replaces the legacy {@code accountPeriod}. Same job, but the cycle is no longer user-defined —
 * it arrives inside the imported statement. Statement cycles are frequently not calendar months (a
 * card may close on the 18th), which is exactly why the old model conflated them with budgeting
 * periods and forced manual entry.
 *
 * <p>{@code closingBalance} follows the project sign convention: negative means owed.
 */
@Entity
@Table(name = "statement")
public class Statement extends UserOwned {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal closingBalance;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    protected Statement() {
    }

    public Statement(Long userId, Account account, LocalDate periodStart, LocalDate periodEnd,
                     BigDecimal closingBalance) {
        super(userId);
        this.account = account;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.closingBalance = closingBalance;
    }

    public Account getAccount() {
        return account;
    }

    public ImportBatch getImportBatch() {
        return importBatch;
    }

    public void setImportBatch(ImportBatch importBatch) {
        this.importBatch = importBatch;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }

    public Instant getReconciledAt() {
        return reconciledAt;
    }

    public void setReconciledAt(Instant reconciledAt) {
        this.reconciledAt = reconciledAt;
    }
}
