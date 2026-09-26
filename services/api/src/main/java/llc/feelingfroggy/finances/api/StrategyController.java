package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.ai.BacktestClient;
import llc.feelingfroggy.finances.domain.BacktestRun;
import llc.feelingfroggy.finances.domain.Strategy;
import llc.feelingfroggy.finances.service.StrategyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Strategies and backtests (M7c, D-19). A strategy proposes drafts; a backtest is a kept claim. */
@RestController
@RequestMapping("/api/v1")
public class StrategyController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final StrategyService service;
    private final CurrentUser currentUser;

    public StrategyController(StrategyService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/strategies/catalog")
    public List<BacktestClient.WireStrategyInfo> catalog() {
        return service.catalog();
    }

    @GetMapping("/strategies")
    public List<StrategyView> strategies() {
        return service.all(currentUser.id()).stream().map(this::view).toList();
    }

    @PostMapping("/strategies")
    @ResponseStatus(HttpStatus.CREATED)
    public StrategyView save(@Valid @RequestBody Save request) {
        return view(service.save(currentUser.id(), new StrategyService.SaveRequest(request.name(),
            request.kind(), request.params(), request.symbol(), request.timeframe(), request.quantity(),
            request.notes())));
    }

    @PutMapping("/strategies/{id}/active")
    public StrategyView setActive(@PathVariable Long id, @RequestBody Active request) {
        return view(service.setActive(currentUser.id(), id, request.active()));
    }

    @DeleteMapping("/strategies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(currentUser.id(), id);
    }

    /** Ask every active strategy now rather than waiting for the timer. */
    @PostMapping("/strategies/evaluate")
    public List<StrategyService.Outcome> evaluate() {
        return service.evaluateAll(currentUser.id());
    }

    @PostMapping("/backtests")
    @ResponseStatus(HttpStatus.CREATED)
    public BacktestView runBacktest(@Valid @RequestBody Run request) {
        return BacktestView.of(service.runBacktest(currentUser.id(), new StrategyService.RunRequest(
            request.kind(), request.params(), request.symbol(), request.timeframe(), request.start(),
            request.end(), request.initialCash(), request.slippageBps(), request.commission(),
            request.outOfSampleFraction(), request.strategyId())), true);
    }

    @GetMapping("/backtests")
    public List<BacktestView> backtests(@RequestParam(defaultValue = "50") int size) {
        return service.recentRuns(currentUser.id(), size).stream().map(r -> BacktestView.of(r, false)).toList();
    }

    @GetMapping("/backtests/{id}")
    public BacktestView backtest(@PathVariable Long id) {
        return BacktestView.of(service.run(currentUser.id(), id), true);
    }

    @DeleteMapping("/backtests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBacktest(@PathVariable Long id) {
        service.deleteRun(currentUser.id(), id);
    }

    private StrategyView view(Strategy s) {
        return new StrategyView(s.getId(), s.getName(), s.getKind(), service.paramsOf(s.getParams()),
            s.getSecurity().getSymbol(), s.getTimeframe(), s.getQuantity(), s.isActive(), s.getNotes(),
            s.getLastEvaluatedAt(), s.getLastEvaluation(), s.getLastSignalAt(), s.getLastSignal(),
            s.getCreatedAt());
    }

    public record Save(@NotBlank @Size(max = 80) String name, @NotBlank String kind,
                       Map<String, String> params, @NotBlank @Size(max = 16) String symbol,
                       @NotBlank String timeframe, @NotNull BigDecimal quantity,
                       @Size(max = 2000) String notes) {
    }

    public record Active(boolean active) {
    }

    public record Run(@NotBlank String kind, Map<String, String> params, @NotBlank @Size(max = 16) String symbol,
                      @NotBlank String timeframe, @NotNull LocalDate start, @NotNull LocalDate end,
                      BigDecimal initialCash, BigDecimal slippageBps, BigDecimal commission,
                      BigDecimal outOfSampleFraction, Long strategyId) {
    }

    public record StrategyView(Long id, String name, String kind, Map<String, String> params, String symbol,
                               String timeframe, BigDecimal quantity, boolean active, String notes,
                               Instant lastEvaluatedAt, String lastEvaluation, Instant lastSignalAt,
                               String lastSignal, Instant createdAt) {
    }

    /** The summary always; the full result (curve, trades, segments) only when asked for one run. */
    public record BacktestView(Long id, Long strategyId, String kind, Map<String, String> params, String symbol,
                               String timeframe, LocalDate start, LocalDate end, BigDecimal initialCash,
                               BigDecimal slippageBps, BigDecimal commission, BigDecimal outOfSampleFraction,
                               String provider, int bars, int trades, int dayTrades,
                               BigDecimal totalReturnPct, BigDecimal benchmarkReturnPct,
                               BigDecimal maxDrawdownPct, BigDecimal sharpe, BigDecimal finalEquity,
                               BigDecimal inSampleReturnPct, BigDecimal outOfSampleReturnPct,
                               List<String> warnings, Instant createdAt, JsonNode result) {
        static BacktestView of(BacktestRun r, boolean full) {
            Map<String, String> params;
            try {
                params = JSON.readValue(r.getParams(), new tools.jackson.core.type.TypeReference<Map<String, String>>() { });
            } catch (RuntimeException e) {
                params = Map.of();
            }
            List<String> warnings = r.getWarnings() == null || r.getWarnings().isBlank()
                ? List.of() : List.of(r.getWarnings().split("\n"));
            return new BacktestView(r.getId(), r.getStrategyId(), r.getKind(), params, r.getSecurity().getSymbol(),
                r.getTimeframe(), r.getStartDate(), r.getEndDate(), r.getInitialCash(), r.getSlippageBps(),
                r.getCommission(), r.getOutOfSampleFraction(), r.getProvider(), r.getBars(), r.getTrades(),
                r.getDayTrades(), r.getTotalReturnPct(), r.getBenchmarkReturnPct(), r.getMaxDrawdownPct(),
                r.getSharpe(), r.getFinalEquity(), r.getInSampleReturnPct(), r.getOutOfSampleReturnPct(),
                warnings, r.getCreatedAt(), full ? JSON.readTree(r.getResult()) : null);
        }
    }
}
