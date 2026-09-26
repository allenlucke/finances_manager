package llc.feelingfroggy.finances.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.BacktestClient;
import llc.feelingfroggy.finances.domain.BacktestRun;
import llc.feelingfroggy.finances.domain.DomainRuleViolation;
import llc.feelingfroggy.finances.domain.Security;
import llc.feelingfroggy.finances.domain.Strategy;
import llc.feelingfroggy.finances.domain.TradeOrder;
import llc.feelingfroggy.finances.repo.BacktestRunRepository;
import llc.feelingfroggy.finances.repo.StrategyRepository;
import llc.feelingfroggy.finances.repo.TradeOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Strategies and backtests (M7c, D-19).
 *
 * <p>A backtest is run by the Python service and kept whole. A live strategy is asked for its
 * opinion on a timer; a signal becomes a <em>draft</em> order through {@link OrderService},
 * proposed by the assistant with the strategy's reason as the rationale, and the person is told
 * through the notifier. Nothing here confirms, sends, or fills anything.
 */
@Service
public class StrategyService {

    private static final Logger log = LoggerFactory.getLogger(StrategyService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> PARAMS = new TypeReference<>() { };
    private static final Set<String> PENDING = Set.of("draft", "confirmed", "submitted", "accepted", "partially_filled");
    private static final Set<String> DONE = Set.of("filled", "placed_manually");

    private final StrategyRepository strategies;
    private final BacktestRunRepository runs;
    private final TradeOrderRepository orders;
    private final MarketService market;
    private final OrderService orderService;
    private final BacktestClient client;
    private final Notifier notifier;
    private final Clock clock;

    public StrategyService(StrategyRepository strategies, BacktestRunRepository runs,
                           TradeOrderRepository orders, MarketService market, OrderService orderService,
                           BacktestClient client, Notifier notifier, Clock clock) {
        this.strategies = strategies;
        this.runs = runs;
        this.orders = orders;
        this.market = market;
        this.orderService = orderService;
        this.client = client;
        this.notifier = notifier;
        this.clock = clock;
    }

    public record RunRequest(String kind, Map<String, String> params, String symbol, String timeframe,
                             LocalDate start, LocalDate end, BigDecimal initialCash, BigDecimal slippageBps,
                             BigDecimal commission, BigDecimal outOfSampleFraction, Long strategyId) {
    }

    public record SaveRequest(String name, String kind, Map<String, String> params, String symbol,
                              String timeframe, BigDecimal quantity, String notes) {
    }

    /** What one evaluation did for one strategy, in words the screen and the log can both use. */
    public record Outcome(Long strategyId, String name, String symbol, String action, String reason,
                          Long orderId, String note) {
    }

    public List<BacktestClient.WireStrategyInfo> catalog() {
        try {
            return client.catalog();
        } catch (AiServiceClient.AiServiceException e) {
            throw new DomainRuleViolation(e.getMessage());
        }
    }

    @Transactional
    public BacktestRun runBacktest(Long userId, RunRequest r) {
        if (r.end() == null || r.start() == null || r.end().isBefore(r.start())) {
            throw new DomainRuleViolation("The end date is before the start date");
        }
        Security security = market.securityFor(userId, r.symbol());
        Map<String, String> params = r.params() == null ? Map.of() : r.params();
        BigDecimal cash = r.initialCash() == null ? new BigDecimal("10000") : r.initialCash();
        BigDecimal slippage = r.slippageBps() == null ? new BigDecimal("5") : r.slippageBps();
        BigDecimal commission = r.commission() == null ? BigDecimal.ZERO : r.commission();
        BigDecimal oos = r.outOfSampleFraction() == null ? new BigDecimal("0.30") : r.outOfSampleFraction();

        BacktestClient.Backtest result;
        try {
            result = client.run(new BacktestClient.WireBacktestRequest(r.kind(), params,
                security.getSymbol(), r.timeframe(), r.start(), r.end(), cash.toPlainString(),
                slippage.toPlainString(), commission.toPlainString(), oos.toPlainString()));
        } catch (AiServiceClient.AiServiceException refused) {
            throw new DomainRuleViolation(refused.getMessage());
        }
        var run = new BacktestRun(userId, r.strategyId(), r.kind(), JSON.writeValueAsString(params),
            security, r.timeframe(), r.start(), r.end(), cash, slippage, commission, oos);
        run.recordResult(result.provider(), result.bars(), result.trades(), result.dayTrades(),
            result.totalReturnPct(), result.benchmarkReturnPct(), result.maxDrawdownPct(), result.sharpe(),
            result.finalEquity(), result.inSampleReturnPct(), result.outOfSampleReturnPct(),
            String.join("\n", result.warnings()), result.raw());
        log.info("backtest kind={} bars={} trades={}", r.kind(), result.bars(), result.trades());
        return runs.save(run);
    }

    public List<BacktestRun> recentRuns(Long userId, int size) {
        return runs.findRecentForUser(userId, PageRequest.of(0, Math.min(Math.max(size, 1), 200)));
    }

    public BacktestRun run(Long userId, Long id) {
        return runs.findForUser(id, userId).orElseThrow(() -> new DomainRuleViolation("No such backtest"));
    }

    @Transactional
    public void deleteRun(Long userId, Long id) {
        runs.delete(run(userId, id));
    }

    @Transactional
    public Strategy save(Long userId, SaveRequest s) {
        Map<String, String> params = s.params() == null ? Map.of() : s.params();
        var info = catalog().stream().filter(c -> c.kind().equals(s.kind())).findFirst()
            .orElseThrow(() -> new DomainRuleViolation("No strategy called '" + s.kind() + "'"));
        var known = info.params().stream().map(BacktestClient.WireStrategyParam::name).toList();
        for (String name : params.keySet()) {
            if (!known.contains(name)) {
                throw new DomainRuleViolation(s.kind() + " has no parameter '" + name + "'");
            }
        }
        if (info.intraday() && "1Day".equals(s.timeframe())) {
            throw new DomainRuleViolation(info.label() + " is an intraday strategy; choose a timeframe under a day.");
        }
        Security security = market.securityFor(userId, s.symbol());
        return strategies.save(new Strategy(userId, s.name(), s.kind(), JSON.writeValueAsString(params),
            security, s.timeframe(), s.quantity(), s.notes()));
    }

    public List<Strategy> all(Long userId) {
        return strategies.findAllForUser(userId);
    }

    @Transactional
    public Strategy setActive(Long userId, Long id, boolean active) {
        var strategy = strategies.findForUser(id, userId)
            .orElseThrow(() -> new DomainRuleViolation("No such strategy"));
        strategy.setActive(active);
        return strategies.save(strategy);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        strategies.delete(strategies.findForUser(id, userId)
            .orElseThrow(() -> new DomainRuleViolation("No such strategy")));
    }

    /**
     * Asks every active strategy for its opinion and turns a new signal into a draft. One draft
     * at a time per strategy: while one is pending, the strategy waits. A signal from a bar it
     * has already proposed on is not proposed again.
     *
     * @throws llc.feelingfroggy.finances.ai.MarketDataClient.MarketDataOff when no provider is configured
     */
    @Transactional
    public List<Outcome> evaluateAll(Long userId) {
        var outcomes = new ArrayList<Outcome>();
        Instant now = Instant.now(clock);
        for (var strategy : strategies.findActiveForUser(userId)) {
            String symbol = strategy.getSecurity().getSymbol();
            var pending = orders.findTopByStrategyIdAndStatusInOrderByIdDesc(strategy.getId(), PENDING);
            if (pending.isPresent()) {
                strategy.evaluated(now, "waiting on order #" + pending.get().getId());
                outcomes.add(new Outcome(strategy.getId(), strategy.getName(), symbol, null, null,
                    pending.get().getId(), "waiting on an order that is not settled"));
                continue;
            }
            String position = orders.findTopByStrategyIdAndStatusInOrderByIdDesc(strategy.getId(), DONE)
                .map(last -> "buy".equals(last.getSide()) ? "long" : "flat").orElse("flat");
            BacktestClient.WireEvaluateResult result;
            try {
                result = client.evaluate(new BacktestClient.WireEvaluateRequest(strategy.getKind(),
                    JSON.readValue(strategy.getParams(), PARAMS), symbol, strategy.getTimeframe(),
                    position, 300));
            } catch (AiServiceClient.AiServiceException e) {
                strategy.evaluated(now, "could not evaluate: " + e.getMessage());
                outcomes.add(new Outcome(strategy.getId(), strategy.getName(), symbol, null, null, null,
                    e.getMessage()));
                continue;
            }
            if (result.action() == null) {
                strategy.evaluated(now, "no signal on " + result.bars() + " bars ("
                    + position + ", last close " + result.lastClose() + ")");
                outcomes.add(new Outcome(strategy.getId(), strategy.getName(), symbol, null, null, null,
                    "no signal"));
                continue;
            }
            if (result.asOf() != null && result.asOf().equals(strategy.getLastSignalAt())) {
                strategy.evaluated(now, "same signal as before, already proposed");
                outcomes.add(new Outcome(strategy.getId(), strategy.getName(), symbol, result.action(),
                    result.reason(), null, "already proposed from this bar"));
                continue;
            }
            String rationale = "Strategy \"" + strategy.getName() + "\": " + result.reason()
                + (result.asOf() == null ? "" : " (bar " + result.asOf() + ")")
                + (result.safeWarnings().isEmpty() ? "" : ". " + String.join(" ", result.safeWarnings()));
            TradeOrder order = orderService.propose(userId, new OrderService.Proposal(symbol, "paper",
                result.action(), strategy.getQuantity(), "market", null, "day", "assistant", rationale,
                strategy.getId(), result.lastClose()));
            strategy.signalled(result.asOf(), result.action() + ": " + result.reason());
            strategy.evaluated(now, "proposed order #" + order.getId());
            outcomes.add(new Outcome(strategy.getId(), strategy.getName(), symbol, result.action(),
                result.reason(), order.getId(), "draft proposed"));
            notify(strategy, order, result);
        }
        strategies.saveAll(strategies.findActiveForUser(userId));
        return outcomes;
    }

    private void notify(Strategy strategy, TradeOrder order, BacktestClient.WireEvaluateResult result) {
        if (!notifier.configured()) {
            return;
        }
        try {
            notifier.send("Strategy " + strategy.getName() + " proposes " + order.describe(),
                result.reason() + ". Draft #" + order.getId()
                    + " is waiting for your confirmation; nothing is sent until you confirm it.");
        } catch (Notifier.NotificationFailed e) {
            log.warn("strategy notification failed: {}", e.getMessage());
        }
    }

    public Map<String, String> paramsOf(String json) {
        try {
            return JSON.readValue(json == null ? "{}" : json, PARAMS);
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }
}
