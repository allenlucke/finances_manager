package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * How much of one instrument an account held, on one date.
 *
 * <p>A snapshot, not a movement. Nothing in the ledger's debit/credit sign convention applies:
 * {@code marketValue} is a magnitude and is always positive. A positions export contains no money
 * movements at all, so none of this belongs in {@code transaction} — see docs/DOMAIN.md.
 *
 * <p>{@code asOf} is part of the identity, not decoration. Keeping successive snapshots is the only
 * way a position acquires a history, because the file itself has none.
 */
@Entity
@Table(name = "holding")
public class Holding extends UserOwned {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    /**
     * Share count. Not money, so not {@code (19,4)} — mutual funds settle to three decimals and
     * crypto to eight, and rounding a share count would change what someone owns. Null for cash
     * and money-market rows, which carry a value but no quantity.
     */
    @Column(name = "quantity", precision = 28, scale = 8)
    private BigDecimal quantity;

    /** Money, but finer than a ledger amount: sub-cent quotes are ordinary. */
    @Column(name = "last_price", precision = 19, scale = 6)
    private BigDecimal lastPrice;

    /** Not null: a holding whose value is unknown cannot contribute to a balance, and a silent
     * zero would understate net worth rather than announce the gap. */
    @Column(name = "market_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal marketValue;

    /** Null where the export writes {@code --} — not-applicable, which is not zero. */
    @Column(name = "cost_basis", precision = 19, scale = 4)
    private BigDecimal costBasis;

    @Column(name = "average_cost", precision = 19, scale = 6)
    private BigDecimal averageCost;

    @Column(name = "total_gain_loss", precision = 19, scale = 4)
    private BigDecimal totalGainLoss;

    protected Holding() {
    }

    public Holding(Long userId, Account account, Security security, LocalDate asOf,
                   BigDecimal marketValue) {
        super(userId);
        this.account = account;
        this.security = security;
        this.asOf = asOf;
        this.marketValue = marketValue;
    }

    public Account getAccount() {
        return account;
    }

    public Security getSecurity() {
        return security;
    }

    public LocalDate getAsOf() {
        return asOf;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
    }

    public BigDecimal getCostBasis() {
        return costBasis;
    }

    public void setCostBasis(BigDecimal costBasis) {
        this.costBasis = costBasis;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public BigDecimal getTotalGainLoss() {
        return totalGainLoss;
    }

    public void setTotalGainLoss(BigDecimal totalGainLoss) {
        this.totalGainLoss = totalGainLoss;
    }

    public ImportBatch getImportBatch() {
        return importBatch;
    }

    public void setImportBatch(ImportBatch importBatch) {
        this.importBatch = importBatch;
    }
}
