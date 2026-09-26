package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.BacktestClient;
import llc.feelingfroggy.finances.ai.BacktestClient.WireEvaluateResult;
import llc.feelingfroggy.finances.ai.BacktestClient.WireStrategyInfo;
import llc.feelingfroggy.finances.ai.BacktestClient.WireStrategyParam;
import llc.feelingfroggy.finances.ai.BrokerClient;
import llc.feelingfroggy.finances.ai.BrokerClient.WireOrder;
import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.service.Notifier;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Strategies and backtests (M7c, D-19), with the Python service mocked at the wire. Trading is on
 * here so a strategy's draft can be confirmed and filled, which is how the strategy learns it is
 * long.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"finances.trading.enabled=true", "finances.trading.daily-notional-cap=100000.00"})
@DisplayName("Strategies and backtests")
class StrategyTest extends PostgresIntegrationTest {

    private static final String RESULT = """
        {"provider":"fake","strategy":"sma_cross","params":{"fast":"5","slow":"20"},"symbol":"AAPL",
         "timeframe":"1Day","start":"2026-01-05","end":"2026-09-25",
         "metrics":{"bars":188,"trades":7,"total_return_pct":"3.10","benchmark_return_pct":"8.25",
                    "max_drawdown_pct":"6.40","win_rate_pct":"42.86","profit_factor":"1.21",
                    "avg_trade_pct":"0.44","exposure_pct":"51.06","sharpe":0.61,
                    "final_equity":"10310.00","day_trades":0},
         "in_sample":{"bars":131,"trades":5,"total_return_pct":"5.00","benchmark_return_pct":"6.00",
                      "max_drawdown_pct":"4.00","exposure_pct":"50.00","final_equity":"10500.00","day_trades":0},
         "out_of_sample":{"bars":57,"trades":2,"total_return_pct":"-1.80","benchmark_return_pct":"2.10",
                      "max_drawdown_pct":"3.00","exposure_pct":"53.00","final_equity":"9820.00","day_trades":0},
         "equity_curve":[{"ts":"2026-01-05T21:00:00Z","equity":"10000.00"},{"ts":"2026-09-25T20:00:00Z","equity":"10310.00"}],
         "trades":[{"entered_at":"2026-02-02T14:30:00Z","exited_at":"2026-02-20T14:30:00Z","quantity":52,
                    "entry_price":"190.1000","exit_price":"193.0000","pnl":"150.80","return_pct":"1.53",
                    "reason_in":"SMA5 crossed above SMA20","reason_out":"SMA5 crossed below SMA20","same_day":false}],
         "warnings":["These bars are fake: a deterministic random walk from the fake provider. The result says nothing about any market.",
                     "Only 7 closed trade(s): the statistics are mostly noise.",
                     "Buy and hold returned 8.25% over the same bars; the strategy returned 3.10%."]}
        """;

    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private BacktestClient backtests;
    @MockitoBean private BrokerClient broker;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private Notifier notifier;
    @LocalServerPort private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        when(backtests.catalog()).thenReturn(List.of(new WireStrategyInfo("sma_cross",
            "Moving-average crossover", "Long while fast is above slow.", false, List.of(
                new WireStrategyParam("fast", "Fast average", "int", "Bars", "10", "2", "500"),
                new WireStrategyParam("slow", "Slow average", "int", "Bars", "30", "3", "1000")))));
        when(notifier.configured()).thenReturn(true);
    }

    private Map<String, Object> runBody() {
        return Map.of("kind", "sma_cross", "params", Map.of("fast", "5", "slow", "20"), "symbol", "aapl",
            "timeframe", "1Day", "start", "2026-01-05", "end", "2026-09-25", "initialCash", "10000");
    }

    @Test
    @DisplayName("a backtest is kept whole: summary in the list, the full result on request")
    void backtestIsKept() {
        when(backtests.run(any())).thenReturn(BacktestClient.Backtest.of(RESULT));

        var ran = api.postJson("/api/v1/backtests", runBody());

        assertThat(ran.status()).as(ran.body()).isEqualTo(201);
        assertThat(ran.json().get("symbol").asText()).isEqualTo("AAPL");
        assertThat(new BigDecimal(ran.json().get("totalReturnPct").asText())).isEqualByComparingTo("3.10");
        assertThat(new BigDecimal(ran.json().get("benchmarkReturnPct").asText())).isEqualByComparingTo("8.25");
        assertThat(new BigDecimal(ran.json().get("outOfSampleReturnPct").asText())).isEqualByComparingTo("-1.80");
        assertThat(ran.json().get("warnings")).hasSize(3);
        assertThat(ran.json().get("result").get("trades").get(0).get("reason_in").asText())
            .isEqualTo("SMA5 crossed above SMA20");
        var sent = ArgumentCaptor.forClass(BacktestClient.WireBacktestRequest.class);
        verify(backtests).run(sent.capture());
        assertThat(sent.getValue().initialCash()).isEqualTo("10000");
        assertThat(sent.getValue().slippageBps()).isEqualTo("5");
        assertThat(sent.getValue().params()).containsEntry("fast", "5");

        var list = api.get("/api/v1/backtests").json();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("result").isNull()).isTrue();
        assertThat(list.get(0).get("provider").asText()).isEqualTo("fake");
        long id = list.get(0).get("id").asLong();
        assertThat(api.get("/api/v1/backtests/" + id).json().get("result").get("metrics").get("sharpe").asDouble())
            .isEqualTo(0.61);
    }

    @Test
    @DisplayName("a refused backtest is the service's sentence and leaves nothing behind")
    void refusedBacktest() {
        when(backtests.run(any())).thenThrow(new AiServiceClient.AiServiceException("No strategy called 'x'"));

        var refused = api.postJson("/api/v1/backtests", runBody());

        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.body()).contains("No strategy called");
        assertThat(api.get("/api/v1/backtests").json()).isEmpty();
        var backwards = new java.util.HashMap<>(runBody());
        backwards.put("start", "2026-09-25");
        backwards.put("end", "2026-01-05");
        assertThat(api.postJson("/api/v1/backtests", backwards).body()).contains("before the start");
    }

    @Test
    @DisplayName("a live strategy proposes a draft once per signal, tells the person, and never sends")
    void liveStrategyProposesDrafts() {
        var save = api.postJson("/api/v1/strategies", Map.of("name", "Fast cross", "kind", "sma_cross",
            "params", Map.of("fast", "5", "slow", "20"), "symbol", "AAPL", "timeframe", "1Day",
            "quantity", "3", "notes", "trying it on paper"));
        assertThat(save.status()).as(save.body()).isEqualTo(201);
        long strategyId = save.json().get("id").asLong();
        assertThat(api.postJson("/api/v1/strategies", Map.of("name", "Bad", "kind", "sma_cross",
            "params", Map.of("speed", "1"), "symbol", "AAPL", "timeframe", "1Day", "quantity", "1")).body())
            .contains("has no parameter 'speed'");

        // Inactive: evaluate asks nothing.
        assertThat(api.postJson("/api/v1/strategies/evaluate", Map.of()).json()).isEmpty();
        verify(backtests, never()).evaluate(any());

        var switchedOn = api.putJson("/api/v1/strategies/" + strategyId + "/active", Map.of("active", true));
        assertThat(switchedOn.status()).as(switchedOn.body()).isEqualTo(200);
        Instant bar = Instant.parse("2026-09-25T20:00:00Z");
        when(backtests.evaluate(any())).thenReturn(new WireEvaluateResult("fake", "AAPL", 300, bar,
            new BigDecimal("189.30"), "buy", "SMA5 crossed above SMA20", List.of("Fake bars: this signal says nothing about any market.")));

        var evaluated = api.postJson("/api/v1/strategies/evaluate", Map.of());
        assertThat(evaluated.status()).as(evaluated.body()).isEqualTo(200);
        var first = evaluated.json();
        assertThat(first.get(0).get("note").asText()).isEqualTo("draft proposed");
        long orderId = first.get(0).get("orderId").asLong();
        var order = api.get("/api/v1/orders").json().get(0);
        assertThat(order.get("id").asLong()).isEqualTo(orderId);
        assertThat(order.get("status").asText()).isEqualTo("draft");
        assertThat(order.get("proposedBy").asText()).isEqualTo("assistant");
        assertThat(order.get("rationale").asText()).contains("Fast cross").contains("crossed above");
        assertThat(order.get("description").asText()).isEqualTo("buy 3 AAPL at market (paper)");
        // Sized by the bar the signal came from: no stored quote is needed.
        assertThat(new BigDecimal(order.get("notionalEstimate").asText())).isEqualByComparingTo("567.90");
        verify(notifier, times(1)).send(anyString(), anyString());
        verify(broker, never()).submit(any());

        // The same bar again: nothing new. A pending draft: the strategy waits.
        var second = api.postJson("/api/v1/strategies/evaluate", Map.of()).json();
        assertThat(second.get(0).get("note").asText()).contains("waiting on an order");
        assertThat(api.get("/api/v1/orders").json()).hasSize(1);
        var strategy = api.get("/api/v1/strategies").json().get(0);
        assertThat(strategy.get("lastSignal").asText()).isEqualTo("buy: SMA5 crossed above SMA20");
        assertThat(strategy.get("lastEvaluation").asText()).startsWith("waiting on order #");

        // Confirm and fill it; the strategy is now long and is asked as such.
        when(broker.submit(any())).thenReturn(new WireOrder("fake", "b-1", "accepted", "new", null, null, BigDecimal.ZERO, null));
        var confirmed = api.postJson("/api/v1/orders/" + orderId + "/confirm",
            Map.of("symbol", "AAPL", "side", "buy", "quantity", "3"));
        assertThat(confirmed.status()).as(confirmed.body()).isEqualTo(200);
        when(broker.lookup("b-1")).thenReturn(new WireOrder("fake", "b-1", "filled", "filled", null,
            Instant.parse("2026-09-25T20:01:00Z"), new BigDecimal("3"), new BigDecimal("189.40")));
        api.postJson("/api/v1/orders/sync", Map.of());
        when(backtests.evaluate(any())).thenReturn(new WireEvaluateResult("fake", "AAPL", 300,
            Instant.parse("2026-09-28T20:00:00Z"), new BigDecimal("187.00"), "sell", "SMA5 crossed below SMA20", List.of()));

        var third = api.postJson("/api/v1/strategies/evaluate", Map.of()).json();

        var asked = ArgumentCaptor.forClass(BacktestClient.WireEvaluateRequest.class);
        verify(backtests, times(2)).evaluate(asked.capture());
        assertThat(asked.getAllValues().get(0).position()).isEqualTo("flat");
        assertThat(asked.getAllValues().get(1).position()).isEqualTo("long");
        assertThat(third.get(0).get("action").asText()).isEqualTo("sell");
        assertThat(api.get("/api/v1/orders").json().get(0).get("description").asText())
            .isEqualTo("sell 3 AAPL at market (paper)");
        // Nothing here touched the ledger.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class)).isZero();
    }

    @Test
    @DisplayName("with no market data, evaluation says so as a state, not a fault")
    void noProviderIsAState() {
        long id = api.postJson("/api/v1/strategies", Map.of("name", "S", "kind", "sma_cross",
            "symbol", "MSFT", "timeframe", "1Day", "quantity", "1")).json().get("id").asLong();
        api.putJson("/api/v1/strategies/" + id + "/active", Map.of("active", true));
        when(backtests.evaluate(any())).thenThrow(new MarketDataClient.MarketDataOff("MARKET_DATA_PROVIDER is 'none'."));

        var response = api.postJson("/api/v1/strategies/evaluate", Map.of());

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.body()).contains("MARKET_DATA_PROVIDER");
        api.delete("/api/v1/strategies/" + id);
        assertThat(api.get("/api/v1/strategies").json()).isEmpty();
    }
}
