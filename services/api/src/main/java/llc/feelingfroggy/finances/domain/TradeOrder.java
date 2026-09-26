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
import java.util.UUID;

/**
 * An order, from proposal to fill (M7b, D-18).
 *
 * <p>The life is a state machine the database can see: {@code draft} → {@code confirmed} →
 * {@code submitted} → {@code accepted} → {@code partially_filled} → {@code filled}, with
 * {@code cancelled}, {@code rejected}, {@code expired} and {@code failed} as the ways out, and
 * {@code placed_manually} for a ticket carried to Fidelity by hand. Every transition is a
 * {@link TradeOrderEvent} with an actor.
 *
 * <p>Nothing here is a ledger row. A fill changes what is owned, and what is owned is learned from
 * the next positions import.
 */
@Entity
@Table(name = "trade_order")
public class TradeOrder extends UserOwned {

    public static final Set<String> OPEN = Set.of("submitted", "accepted", "partially_filled");
    public static final Set<String> TERMINAL =
        Set.of("filled", "placed_manually", "cancelled", "rejected", "expired", "failed");
    /** What counts against the daily cap: everything that reached the broker and was not refused. */
    public static final Set<String> SENT = Set.of("submitted", "accepted", "partially_filled", "filled");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(name = "venue", nullable = false, length = 20)
    private String venue;

    @Column(name = "side", nullable = false, length = 4)
    private String side;

    @Column(name = "quantity", nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "order_type", nullable = false, length = 10)
    private String orderType;

    @Column(name = "limit_price", precision = 19, scale = 6)
    private BigDecimal limitPrice;

    @Column(name = "time_in_force", nullable = false, length = 5)
    private String timeInForce;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "proposed_by", nullable = false, length = 10)
    private String proposedBy;

    @Column(name = "rationale")
    private String rationale;

    @Column(name = "reference_price", precision = 19, scale = 6)
    private BigDecimal referencePrice;

    @Column(name = "notional_estimate", nullable = false, precision = 19, scale = 4)
    private BigDecimal notionalEstimate;

    @Column(name = "client_order_id", nullable = false, length = 48)
    private String clientOrderId;

    @Column(name = "broker", length = 30)
    private String broker;

    @Column(name = "broker_order_id", length = 100)
    private String brokerOrderId;

    @Column(name = "broker_status", length = 40)
    private String brokerStatus;

    @Column(name = "filled_quantity", nullable = false, precision = 28, scale = 8)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Column(name = "filled_avg_price", precision = 19, scale = 6)
    private BigDecimal filledAvgPrice;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "filled_at")
    private Instant filledAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "last_error")
    private String lastError;

    /** The strategy that proposed it, when one did (M7c). */
    @Column(name = "strategy_id")
    private Long strategyId;

    protected TradeOrder() {
    }

    public TradeOrder(Long userId, Security security, String venue, String side, BigDecimal quantity,
                      String orderType, BigDecimal limitPrice, String timeInForce, String proposedBy,
                      String rationale, BigDecimal referencePrice) {
        super(userId);
        if (!Set.of("paper", "manual").contains(venue)) {
            throw new DomainRuleViolation("An order's venue is paper or manual");
        }
        if (!Set.of("buy", "sell").contains(side)) {
            throw new DomainRuleViolation("An order's side is buy or sell");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new DomainRuleViolation("An order needs a positive quantity");
        }
        if (!Set.of("market", "limit").contains(orderType)) {
            throw new DomainRuleViolation("An order's type is market or limit");
        }
        if ("limit".equals(orderType) && (limitPrice == null || limitPrice.signum() <= 0)) {
            throw new DomainRuleViolation("A limit order needs a positive limit price");
        }
        if (!Set.of("day", "gtc").contains(timeInForce)) {
            throw new DomainRuleViolation("Time in force is day or gtc");
        }
        if (!Set.of("person", "assistant").contains(proposedBy)) {
            throw new DomainRuleViolation("An order is proposed by the person or the assistant");
        }
        BigDecimal priceForSizing = "limit".equals(orderType) ? limitPrice : referencePrice;
        if (priceForSizing == null) {
            throw new DomainRuleViolation("No quote for " + security.getSymbol()
                + " yet, so the order cannot be sized. Refresh quotes first, or give a limit price.");
        }
        this.security = security;
        this.venue = venue;
        this.side = side;
        this.quantity = quantity;
        this.orderType = orderType;
        this.limitPrice = limitPrice;
        this.timeInForce = timeInForce;
        this.proposedBy = proposedBy;
        this.rationale = rationale;
        this.referencePrice = referencePrice;
        this.notionalEstimate = quantity.multiply(priceForSizing).setScale(4, RoundingMode.HALF_UP);
        this.clientOrderId = "fm-" + UUID.randomUUID();
    }

    /**
     * The confirmation is an echo: the person restates what is about to be sent, and it must be
     * what the draft says. A confirmation that matches nothing in particular confirms nothing.
     *
     * <p>An order already confirmed but never sent — trading was off, or no broker answered — may
     * be confirmed again, with the same echo, to try the gates once more.
     */
    public void confirm(String symbol, String echoedSide, BigDecimal echoedQuantity,
                        BigDecimal echoedLimit, Instant now) {
        if (!"draft".equals(status) && !"confirmed".equals(status)) {
            throw new DomainRuleViolation("Cannot confirm an order that is " + status);
        }
        boolean matches = security.getSymbol().equalsIgnoreCase(symbol == null ? "" : symbol.strip())
            && side.equals(echoedSide)
            && echoedQuantity != null && quantity.compareTo(echoedQuantity) == 0
            && (limitPrice == null ? echoedLimit == null
                : echoedLimit != null && limitPrice.compareTo(echoedLimit) == 0);
        if (!matches) {
            throw new DomainRuleViolation("The confirmation does not match the draft: it is "
                + describe() + ". Restate exactly that to confirm it.");
        }
        this.status = "confirmed";
        this.confirmedAt = now;
    }

    public void submitted(String broker, String brokerOrderId, String folded, String brokerStatus,
                          Instant now) {
        requireStatus("confirmed", "submit");
        this.broker = broker;
        this.brokerOrderId = brokerOrderId;
        this.submittedAt = now;
        this.status = "submitted";
        applyBrokerUpdate(folded, brokerStatus, null, null, null, now);
    }

    /** What the broker says, folded to this machine's states. Idempotent. */
    public void applyBrokerUpdate(String folded, String brokerStatus, BigDecimal filledQuantity,
                                  BigDecimal filledAvgPrice, Instant filledAt, Instant now) {
        this.brokerStatus = brokerStatus;
        if (filledQuantity != null) {
            this.filledQuantity = filledQuantity;
        }
        if (filledAvgPrice != null) {
            this.filledAvgPrice = filledAvgPrice;
        }
        switch (folded == null ? "" : folded) {
            case "accepted" -> this.status = "accepted";
            case "partially_filled" -> this.status = "partially_filled";
            case "filled" -> {
                this.status = "filled";
                this.filledAt = filledAt == null ? now : filledAt;
                this.closedAt = now;
            }
            case "cancelled", "rejected", "expired" -> {
                this.status = folded;
                this.closedAt = now;
            }
            default -> { /* unknown broker word: keep our state, keep their word in brokerStatus */ }
        }
    }

    public void failed(String why, Instant now) {
        this.status = "failed";
        this.lastError = why;
        this.closedAt = now;
    }

    public void cancelledLocally(Instant now) {
        if (!Set.of("draft", "confirmed").contains(status)) {
            throw new DomainRuleViolation("Only a draft or a confirmed-but-unsent order can be "
                + "cancelled here; an order at the broker is cancelled through the broker.");
        }
        this.status = "cancelled";
        this.closedAt = now;
    }

    /** A manual ticket the person has placed at Fidelity by hand. */
    public void placedManually(BigDecimal fillPrice, Instant now) {
        if (!"manual".equals(venue)) {
            throw new DomainRuleViolation("Only a manual ticket is marked placed by hand");
        }
        requireStatus("confirmed", "mark placed");
        this.status = "placed_manually";
        this.filledQuantity = quantity;
        this.filledAvgPrice = fillPrice;
        this.filledAt = now;
        this.closedAt = now;
    }

    private void requireStatus(String expected, String verb) {
        if (!expected.equals(status)) {
            throw new DomainRuleViolation("Cannot " + verb + " an order that is " + status);
        }
    }

    public boolean isOpen() {
        return OPEN.contains(status);
    }

    public String describe() {
        return side + " " + quantity.stripTrailingZeros().toPlainString() + " " + security.getSymbol()
            + ("limit".equals(orderType)
                ? " at a limit of " + limitPrice.stripTrailingZeros().toPlainString()
                : " at market")
            + " (" + venue + ")";
    }

    public Security getSecurity() { return security; }
    public String getVenue() { return venue; }
    public String getSide() { return side; }
    public BigDecimal getQuantity() { return quantity; }
    public String getOrderType() { return orderType; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public String getTimeInForce() { return timeInForce; }
    public String getStatus() { return status; }
    public String getProposedBy() { return proposedBy; }
    public String getRationale() { return rationale; }
    public BigDecimal getReferencePrice() { return referencePrice; }
    public BigDecimal getNotionalEstimate() { return notionalEstimate; }
    public String getClientOrderId() { return clientOrderId; }
    public String getBroker() { return broker; }
    public String getBrokerOrderId() { return brokerOrderId; }
    public String getBrokerStatus() { return brokerStatus; }
    public BigDecimal getFilledQuantity() { return filledQuantity; }
    public BigDecimal getFilledAvgPrice() { return filledAvgPrice; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getFilledAt() { return filledAt; }
    public Instant getClosedAt() { return closedAt; }
    public String getLastError() { return lastError; }
    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
}
