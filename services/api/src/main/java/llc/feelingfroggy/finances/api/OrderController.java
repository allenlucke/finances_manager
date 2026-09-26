package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import llc.feelingfroggy.finances.ai.BrokerClient;
import llc.feelingfroggy.finances.config.TradingProperties;
import llc.feelingfroggy.finances.domain.TradeOrder;
import llc.feelingfroggy.finances.domain.TradeOrderEvent;
import llc.feelingfroggy.finances.repo.TradeOrderRepository;
import llc.feelingfroggy.finances.service.OrderService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Orders (M7b, D-18): propose, confirm by restating, execute within a cap, or carry to Fidelity by
 * hand. Every refusal is a sentence written for the person; every transition is on record.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService service;
    private final TradeOrderRepository orders;
    private final BrokerClient broker;
    private final TradingProperties trading;
    private final CurrentUser currentUser;

    public OrderController(OrderService service, TradeOrderRepository orders, BrokerClient broker,
                           TradingProperties trading, CurrentUser currentUser) {
        this.service = service;
        this.orders = orders;
        this.broker = broker;
        this.trading = trading;
        this.currentUser = currentUser;
    }

    /** The switch, the cap, what today has used of it, and what the broker says about itself. */
    @GetMapping("/status")
    public TradingStatusView status() {
        var used = service.usedToday(currentUser.id());
        return new TradingStatusView(trading.enabled(), trading.dailyNotionalCap(), used,
            trading.dailyNotionalCap().subtract(used).max(BigDecimal.ZERO), broker.status());
    }

    @GetMapping
    public List<OrderView> recent(@RequestParam(defaultValue = "100") int size) {
        return orders.findRecentForUser(currentUser.id(), PageRequest.of(0, Math.min(size, 500)))
            .stream().map(OrderView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView propose(@Valid @RequestBody Propose request) {
        return OrderView.of(service.propose(currentUser.id(), new OrderService.Proposal(
            request.symbol(), request.venue() == null ? "paper" : request.venue(), request.side(),
            request.quantity(), request.orderType(), request.limitPrice(), request.timeInForce(),
            request.proposedBy(), request.rationale(), null, null)));
    }

    /** The echo is the confirmation: symbol, side, quantity and limit must be the draft's. */
    @PostMapping("/{id}/confirm")
    public OrderView confirm(@PathVariable Long id, @Valid @RequestBody Confirm request) {
        return OrderView.of(service.confirm(currentUser.id(), id, request.symbol(), request.side(),
            request.quantity(), request.limitPrice(), request.actor() == null ? "person" : request.actor()));
    }

    @PostMapping("/{id}/cancel")
    public OrderView cancel(@PathVariable Long id, @RequestBody(required = false) Actor request) {
        return OrderView.of(service.cancel(currentUser.id(), id,
            request == null || request.actor() == null ? "person" : request.actor()));
    }

    /** A manual ticket the person has placed at Fidelity, optionally with the fill price. */
    @PostMapping("/{id}/placed")
    public OrderView placed(@PathVariable Long id, @RequestBody(required = false) Placed request) {
        return OrderView.of(service.markPlaced(currentUser.id(), id,
            request == null ? null : request.fillPrice(),
            request == null || request.actor() == null ? "person" : request.actor()));
    }

    @PostMapping("/sync")
    public SyncView sync() {
        return new SyncView(service.sync(currentUser.id()));
    }

    @GetMapping("/{id}/events")
    public List<OrderEventView> events(@PathVariable Long id) {
        return service.eventsFor(currentUser.id(), id).stream().map(OrderEventView::of).toList();
    }

    public record Propose(@NotBlank @Size(max = 16) String symbol, String venue,
                          @NotBlank String side, @NotNull BigDecimal quantity, String orderType,
                          BigDecimal limitPrice, String timeInForce, String proposedBy,
                          @Size(max = 2000) String rationale) {
    }

    public record Confirm(@NotBlank String symbol, @NotBlank String side, @NotNull BigDecimal quantity,
                          BigDecimal limitPrice, String actor) {
    }

    public record Actor(String actor) {
    }

    public record Placed(BigDecimal fillPrice, String actor) {
    }

    public record SyncView(int changed) {
    }

    public record TradingStatusView(boolean enabled, BigDecimal dailyNotionalCap, BigDecimal usedToday,
                                    BigDecimal remainingToday, BrokerClient.WireBrokerStatus broker) {
    }

    public record OrderView(Long id, String symbol, String venue, String side, BigDecimal quantity,
                            String orderType, BigDecimal limitPrice, String timeInForce, String status,
                            String proposedBy, String rationale, BigDecimal referencePrice,
                            BigDecimal notionalEstimate, String broker, String brokerOrderId,
                            String brokerStatus, BigDecimal filledQuantity, BigDecimal filledAvgPrice,
                            Instant createdAt, Instant confirmedAt, Instant submittedAt, Instant filledAt,
                            Instant closedAt, String lastError, String description) {
        static OrderView of(TradeOrder o) {
            return new OrderView(o.getId(), o.getSecurity().getSymbol(), o.getVenue(), o.getSide(),
                o.getQuantity(), o.getOrderType(), o.getLimitPrice(), o.getTimeInForce(), o.getStatus(),
                o.getProposedBy(), o.getRationale(), o.getReferencePrice(), o.getNotionalEstimate(),
                o.getBroker(), o.getBrokerOrderId(), o.getBrokerStatus(), o.getFilledQuantity(),
                o.getFilledAvgPrice(), o.getCreatedAt(), o.getConfirmedAt(), o.getSubmittedAt(),
                o.getFilledAt(), o.getClosedAt(), o.getLastError(), o.describe());
        }
    }

    public record OrderEventView(Long id, Instant at, String fromStatus, String toStatus, String actor,
                                 String note) {
        static OrderEventView of(TradeOrderEvent e) {
            return new OrderEventView(e.getId(), e.getAt(), e.getFromStatus(), e.getToStatus(),
                e.getActor(), e.getNote());
        }
    }
}
