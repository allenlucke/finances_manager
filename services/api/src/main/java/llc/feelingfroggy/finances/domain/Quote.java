package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One observed price for one security at one moment (M7a).
 *
 * <p>An observation, not a ledger fact: immutable once written, so it does not extend
 * {@link BaseEntity} (no version, no updated_at). A price has no direction and never changes a
 * balance; it multiplies a share count into a market value that is shown beside the snapshot's,
 * labelled, and never in place of it.
 */
@Entity
@Table(name = "quote")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    /** The vendor's timestamp for the trade, not when this row was written. */
    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    @Column(name = "price", nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    @Column(name = "previous_close", precision = 19, scale = 6)
    private BigDecimal previousClose;

    /** "alpaca", "fake" — shown, so a fake price is never mistaken for a real one. */
    @Column(name = "source", nullable = false, length = 30)
    private String source;

    @Column(name = "fetched_at", insertable = false, updatable = false)
    private Instant fetchedAt;

    protected Quote() {
    }

    public Quote(Long userId, Security security, Instant asOf, BigDecimal price,
                 BigDecimal previousClose, String source) {
        this.userId = userId;
        this.security = security;
        this.asOf = asOf;
        this.price = price;
        this.previousClose = previousClose;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Security getSecurity() {
        return security;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
