package llc.feelingfroggy.finances.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.BrokerClient;
import llc.feelingfroggy.finances.config.TradingProperties;
import llc.feelingfroggy.finances.domain.DomainRuleViolation;
import llc.feelingfroggy.finances.domain.Security;
import llc.feelingfroggy.finances.domain.TradeOrder;
import llc.feelingfroggy.finances.domain.TradeOrderEvent;
import llc.feelingfroggy.finances.repo.QuoteRepository;
import llc.feelingfroggy.finances.repo.TradeOrderEventRepository;
import llc.feelingfroggy.finances.repo.TradeOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Propose, confirm, execute (M7b, D-18).
 *
 * <p>Three gates stand between a draft and a broker, in this order: the person's confirmation,
 * which restates the order and must match it; the kill switch, off by default; and the daily
 * notional cap. A manual ticket clears only the first — it goes to Fidelity in the person's own
 * hands, and is marked placed afterwards. Every transition is recorded with its actor.
 *
 * <p>Nothing here writes a ledger row. A fill changes what is owned, and what is owned is learned
 * from the next positions import.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final TradeOrderRepository orders;
    private final TradeOrderEventRepository events;
    private final QuoteRepository quotes;
    private final MarketService market;
    private final BrokerClient broker;
    private final TradingProperties trading;
    private final Clock clock;

    public OrderService(TradeOrderRepository orders, TradeOrderEventRepository events,
                        QuoteRepository quotes, MarketService market, BrokerClient broker,
                        TradingProperties trading, Clock clock) {
        this.orders = orders;
        this.events = events;
        this.quotes = quotes;
        this.market = market;
        this.broker = broker;
        this.trading = trading;
        this.clock = clock;
    }

    /**
     * @param referencePrice a price to size a market order by when the caller has a fresher one
     *     than the stored quote — a strategy's last bar close. Null means use the latest quote.
     */
    public record Proposal(String symbol, String venue, String side, BigDecimal quantity,
                           String orderType, BigDecimal limitPrice, String timeInForce,
                           String proposedBy, String rationale, Long strategyId,
                           BigDecimal referencePrice) {
    }

    /** A draft: sized against the latest quote (or the limit price), committed to nothing. */
    @Transactional
    public TradeOrder propose(Long userId, Proposal p) {
        Security security = market.securityFor(userId, p.symbol());
        BigDecimal reference = p.referencePrice() != null ? p.referencePrice()
            : quotes.findTopBySecurityIdOrderByAsOfDescIdDesc(security.getId())
                .map(q -> q.getPrice()).orElse(null);
        var order = new TradeOrder(userId, security, p.venue(), p.side(), p.quantity(),
            p.orderType() == null ? "market" : p.orderType(), p.limitPrice(),
            p.timeInForce() == null ? "day" : p.timeInForce(),
            p.proposedBy() == null ? "person" : p.proposedBy(), p.rationale(), reference);
        order.setStrategyId(p.strategyId());
        orders.save(order);
        record(order, null, "draft", order.getProposedBy(), p.rationale());
        return order;
    }

    /**
     * The person's confirmation, as an echo of the draft; then the switch, then the cap; then
     * the broker. A manual ticket stops at "confirmed" and waits to be marked placed.
     *
     * <p>A refusal here is an outcome, not a fault, and the order's state after it — confirmed but
     * not sent, cancelled by the cap, failed at the broker — is the record of what happened. So a
     * {@link DomainRuleViolation} thrown from this method does not roll the transaction back; the
     * same rule as "audit records get their own transaction", applied to the order itself. Every
     * throw below comes after the state it means to keep.
     */
    @Transactional(noRollbackFor = DomainRuleViolation.class)
    public TradeOrder confirm(Long userId, Long id, String symbol, String side, BigDecimal quantity,
                              BigDecimal limitPrice, String actor) {
        var order = orders.findForUser(id, userId)
            .orElseThrow(() -> new DomainRuleViolation("No such order"));
        Instant now = Instant.now(clock);
        String before = order.getStatus();
        order.confirm(symbol, side, quantity, limitPrice, now);

        if ("manual".equals(order.getVenue())) {
            record(order, before, "confirmed", actor, "a ticket for Fidelity; mark it placed once it is");
            return orders.save(order);
        }

        if (!trading.enabled()) {
            // Recorded as confirmed and left there: the person meant it; the system is off.
            record(order, before, "confirmed", actor, "trading is off (TRADING_ENABLED=false); not sent");
            orders.save(order);
            throw new DomainRuleViolation("Trading is off (TRADING_ENABLED=false). The order is "
                + "recorded as confirmed and was not sent; turn trading on and confirm it again.");
        }

        BigDecimal usedToday = usedToday(userId);
        BigDecimal after = usedToday.add(order.getNotionalEstimate());
        if (after.compareTo(trading.dailyNotionalCap()) > 0) {
            String why = "Over the daily cap: " + usedToday.toPlainString() + " already sent today, "
                + "this order adds " + order.getNotionalEstimate().toPlainString() + ", and the cap is "
                + trading.dailyNotionalCap().toPlainString() + " (TRADING_DAILY_CAP).";
            record(order, before, "confirmed", actor, null);
            order.cancelledLocally(now);
            record(order, "confirmed", "cancelled", "system", why);
            orders.save(order);
            throw new DomainRuleViolation(why + " The order is cancelled; propose a smaller one.");
        }

        record(order, before, "confirmed", actor, null);
        BrokerClient.WireOrder sent;
        try {
            sent = broker.submit(new BrokerClient.OrderRequest(order.getClientOrderId(),
                order.getSecurity().getSymbol(), order.getSide(),
                order.getQuantity().stripTrailingZeros().toPlainString(), order.getOrderType(),
                order.getLimitPrice() == null ? null : order.getLimitPrice().stripTrailingZeros().toPlainString(),
                order.getTimeInForce()));
        } catch (BrokerClient.BrokerOff off) {
            record(order, "confirmed", "confirmed", "system", off.getMessage() + " Not sent.");
            orders.save(order);
            throw new DomainRuleViolation(off.getMessage() + " The order stays confirmed and was not sent.");
        } catch (AiServiceClient.AiServiceException refused) {
            order.failed(refused.getMessage(), now);
            record(order, "confirmed", "failed", "broker", refused.getMessage());
            orders.save(order);
            throw new DomainRuleViolation("The broker refused the order: " + refused.getMessage());
        }
        order.submitted(sent.broker(), sent.brokerOrderId(), sent.status(), sent.brokerStatus(), now);
        record(order, "confirmed", order.getStatus(), "broker", sent.brokerStatus());
        log.info("order submitted id={} status={}", order.getId(), order.getStatus());
        return orders.save(order);
    }

    /** Asks the broker about every open order. Returns how many changed state. */
    @Transactional
    public int sync(Long userId) {
        int changed = 0;
        for (var order : orders.findOpenAtBroker(userId)) {
            String before = order.getStatus();
            BrokerClient.WireOrder current;
            try {
                current = broker.lookup(order.getBrokerOrderId());
            } catch (BrokerClient.BrokerOff off) {
                return changed;
            } catch (AiServiceClient.AiServiceException e) {
                log.warn("order sync failed for id={}: {}", order.getId(), e.getMessage());
                continue;
            }
            order.applyBrokerUpdate(current.status(), current.brokerStatus(), current.filledQuantity(),
                current.filledAvgPrice(), current.filledAt(), Instant.now(clock));
            if (!before.equals(order.getStatus())) {
                record(order, before, order.getStatus(), "broker", current.brokerStatus());
                changed++;
            }
            orders.save(order);
        }
        return changed;
    }

    @Transactional
    public TradeOrder cancel(Long userId, Long id, String actor) {
        var order = orders.findForUser(id, userId)
            .orElseThrow(() -> new DomainRuleViolation("No such order"));
        String before = order.getStatus();
        Instant now = Instant.now(clock);
        if (order.isOpen()) {
            BrokerClient.WireOrder current;
            try {
                current = broker.cancel(order.getBrokerOrderId());
            } catch (BrokerClient.BrokerOff off) {
                throw new DomainRuleViolation(off.getMessage());
            } catch (AiServiceClient.AiServiceException e) {
                throw new DomainRuleViolation("The broker could not cancel it: " + e.getMessage());
            }
            order.applyBrokerUpdate(current.status(), current.brokerStatus(), current.filledQuantity(),
                current.filledAvgPrice(), current.filledAt(), now);
        } else {
            order.cancelledLocally(now);
        }
        record(order, before, order.getStatus(), actor, null);
        return orders.save(order);
    }

    @Transactional
    public TradeOrder markPlaced(Long userId, Long id, BigDecimal fillPrice, String actor) {
        var order = orders.findForUser(id, userId)
            .orElseThrow(() -> new DomainRuleViolation("No such order"));
        String before = order.getStatus();
        order.placedManually(fillPrice, Instant.now(clock));
        record(order, before, order.getStatus(), actor,
            fillPrice == null ? "placed at Fidelity" : "placed at Fidelity, filled at " + fillPrice.toPlainString());
        return orders.save(order);
    }

    /** What today's paper orders sent to the broker add up to, in the configured zone's "today". */
    public BigDecimal usedToday(Long userId) {
        Instant startOfDay = LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
        return orders.sentNotionalSince(userId, TradeOrder.SENT, startOfDay);
    }

    public List<TradeOrderEvent> eventsFor(Long userId, Long orderId) {
        return events.findByOrderIdAndUserIdOrderByAtAscIdAsc(orderId, userId);
    }

    private void record(TradeOrder order, String from, String to, String actor, String note) {
        events.save(new TradeOrderEvent(order.getUserId(), order, Instant.now(clock), from, to, actor, note));
    }
}
