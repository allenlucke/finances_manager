package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;

/**
 * A rule on a security's price (M7a): above a level, below a level, or moved more than a
 * percentage against the previous close today.
 *
 * <p>{@code armed} is the edge detector. The alert fires when its condition becomes true while
 * armed, then disarms; it re-arms when the condition is false again — so "AAPL above 190" fires
 * once when AAPL crosses 190, not on every poll for as long as it stays there. A percent-move
 * alert also re-arms each trading day, because its condition is measured against that day's
 * previous close; {@code referenceClose} remembers which close it last saw.
 */
@Entity
@Table(name = "price_alert")
public class PriceAlert extends UserOwned {

    public static final Set<String> RULES = Set.of("above", "below", "pct_move");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(name = "rule", nullable = false, length = 20)
    private String rule;

    @Column(name = "threshold", nullable = false, precision = 19, scale = 6)
    private BigDecimal threshold;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "armed", nullable = false)
    private boolean armed = true;

    @Column(name = "last_fired_at")
    private Instant lastFiredAt;

    @Column(name = "reference_close", precision = 19, scale = 6)
    private BigDecimal referenceClose;

    @Column(name = "note")
    private String note;

    protected PriceAlert() {
    }

    public PriceAlert(Long userId, Security security, String rule, BigDecimal threshold, String note) {
        super(userId);
        if (!RULES.contains(rule)) {
            throw new DomainRuleViolation("An alert rule is one of above, below or pct_move");
        }
        if (threshold == null || threshold.signum() <= 0) {
            throw new DomainRuleViolation("An alert threshold is a positive number");
        }
        this.security = security;
        this.rule = rule;
        this.threshold = threshold;
        this.note = note;
    }

    /** Whether the rule holds for this quote. Pure; the arming state is handled in {@link #observe}. */
    public boolean conditionHolds(Quote quote) {
        return switch (rule) {
            case "above" -> quote.getPrice().compareTo(threshold) > 0;
            case "below" -> quote.getPrice().compareTo(threshold) < 0;
            case "pct_move" -> {
                BigDecimal close = quote.getPreviousClose();
                if (close == null || close.signum() == 0) {
                    yield false;
                }
                BigDecimal move = quote.getPrice().subtract(close).abs()
                    .divide(close, 8, RoundingMode.HALF_UP)
                    .movePointRight(2);
                yield move.compareTo(threshold) >= 0;
            }
            default -> false;
        };
    }

    /**
     * Feeds one quote through the edge detector. Returns true exactly when the alert should fire.
     *
     * <p>A percent-move alert re-arms when the previous close it is measured against changes — a
     * new trading day — so a stock that moved 5% on Monday and again on Tuesday says so twice.
     */
    public boolean observe(Quote quote, Instant now) {
        if (!active) {
            return false;
        }
        if ("pct_move".equals(rule)) {
            BigDecimal close = quote.getPreviousClose();
            if (close != null && (referenceClose == null || referenceClose.compareTo(close) != 0)) {
                referenceClose = close;
                armed = true;
            }
        }
        boolean holds = conditionHolds(quote);
        if (!holds) {
            armed = true;
            return false;
        }
        if (!armed) {
            return false;
        }
        armed = false;
        lastFiredAt = now;
        return true;
    }

    /** What the person reads when it fires. */
    public String describe(Quote quote) {
        String symbol = security.getSymbol();
        return switch (rule) {
            case "above" -> symbol + " is above " + threshold.stripTrailingZeros().toPlainString()
                + ": " + quote.getPrice().stripTrailingZeros().toPlainString();
            case "below" -> symbol + " is below " + threshold.stripTrailingZeros().toPlainString()
                + ": " + quote.getPrice().stripTrailingZeros().toPlainString();
            default -> symbol + " moved more than " + threshold.stripTrailingZeros().toPlainString()
                + "% today: " + quote.getPrice().stripTrailingZeros().toPlainString()
                + " against a close of "
                + (quote.getPreviousClose() == null ? "?" : quote.getPreviousClose().stripTrailingZeros().toPlainString());
        };
    }

    public Security getSecurity() {
        return security;
    }

    public String getRule() {
        return rule;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            this.armed = true;
        }
    }

    public boolean isArmed() {
        return armed;
    }

    public Instant getLastFiredAt() {
        return lastFiredAt;
    }

    public String getNote() {
        return note;
    }
}
