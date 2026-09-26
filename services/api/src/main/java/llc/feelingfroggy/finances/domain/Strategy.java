package llc.feelingfroggy.finances.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * A named rule over bars (M7c, D-19). Active means it is asked for its opinion on a timer and a
 * signal becomes a <em>draft</em> order for a person to confirm. It never trades by itself.
 */
@Entity
@Table(name = "strategy")
public class Strategy extends UserOwned {

    public static final Set<String> TIMEFRAMES = Set.of("1Min", "5Min", "15Min", "1Hour", "1Day");

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    /** A JSON object of parameter name → decimal-as-string. Validated by the Python catalog. */
    @Column(name = "params", nullable = false)
    private String params;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(name = "timeframe", nullable = false, length = 8)
    private String timeframe;

    /** Shares per proposal when live. The size is the person's, not the strategy's. */
    @Column(name = "quantity", nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "notes")
    private String notes;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    @Column(name = "last_evaluation")
    private String lastEvaluation;

    @Column(name = "last_signal_at")
    private Instant lastSignalAt;

    @Column(name = "last_signal")
    private String lastSignal;

    protected Strategy() {
    }

    public Strategy(Long userId, String name, String kind, String params, Security security,
                    String timeframe, BigDecimal quantity, String notes) {
        super(userId);
        if (name == null || name.isBlank()) {
            throw new DomainRuleViolation("A strategy needs a name");
        }
        if (!TIMEFRAMES.contains(timeframe)) {
            throw new DomainRuleViolation("Timeframe is one of 1Min, 5Min, 15Min, 1Hour, 1Day");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new DomainRuleViolation("A strategy needs a positive number of shares per signal");
        }
        this.name = name.strip();
        this.kind = kind;
        this.params = params == null ? "{}" : params;
        this.security = security;
        this.timeframe = timeframe;
        this.quantity = quantity;
        this.notes = notes;
    }

    public void evaluated(Instant at, String outcome) {
        this.lastEvaluatedAt = at;
        this.lastEvaluation = outcome;
    }

    public void signalled(Instant barAt, String signal) {
        this.lastSignalAt = barAt;
        this.lastSignal = signal;
    }

    public void setActive(boolean active) { this.active = active; }

    public String getName() { return name; }
    public String getKind() { return kind; }
    public String getParams() { return params; }
    public Security getSecurity() { return security; }
    public String getTimeframe() { return timeframe; }
    public BigDecimal getQuantity() { return quantity; }
    public boolean isActive() { return active; }
    public String getNotes() { return notes; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public String getLastEvaluation() { return lastEvaluation; }
    public Instant getLastSignalAt() { return lastSignalAt; }
    public String getLastSignal() { return lastSignal; }
}
