package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Strategies and backtests, run by the Python service (M7c).
 *
 * <p>The API never computes an indicator or a return: it sends the request, keeps what comes
 * back, and turns a live signal into a draft order. A backtest result is kept as the JSON the
 * service returned, with a summary read out of it for lists; the wire shape is the service's
 * ({@code finances_ai.models.BacktestResult}), and this side does not re-model it.
 */
@Component
public class BacktestClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;

    public BacktestClient(AiServiceProperties properties, RestClient.Builder builder) {
        var httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.timeout())
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // A year of minute bars takes the service a few seconds; longer than a parse.
        requestFactory.setReadTimeout(java.time.Duration.ofSeconds(120));
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    public List<WireStrategyInfo> catalog() {
        return call(() -> restClient.get().uri("/strategies").retrieve()
            .body(new ParameterizedTypeReference<List<WireStrategyInfo>>() { }));
    }

    /** Runs a backtest and returns the service's result whole, with a summary read from it. */
    public Backtest run(WireBacktestRequest request) {
        String raw = call(() -> restClient.post().uri("/backtests").body(request).retrieve().body(String.class));
        return Backtest.of(raw);
    }

    public WireEvaluateResult evaluate(WireEvaluateRequest request) {
        return call(() -> restClient.post().uri("/strategies/evaluate").body(request).retrieve()
            .body(WireEvaluateResult.class));
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            var result = request.get();
            if (result == null) {
                throw new AiServiceClient.AiServiceException("The strategy service returned nothing.");
            }
            return result;
        } catch (RestClientResponseException e) {
            String detail = AiServiceClient.detailOf(e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 503) {
                throw new MarketDataClient.MarketDataOff(detail == null ? "Market data is not configured." : detail);
            }
            throw new AiServiceClient.AiServiceException(
                detail == null ? "The strategy service refused the request." : detail, e);
        } catch (AiServiceClient.AiServiceException | MarketDataClient.MarketDataOff e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceClient.AiServiceException("The strategy service could not be reached.", e);
        }
    }

    public record WireStrategyParam(String name, String label, String type, String description,
                                    @JsonProperty("default") String defaultValue, String min, String max) {
    }

    public record WireStrategyInfo(String kind, String label, String description, boolean intraday,
                                   List<WireStrategyParam> params) {
    }

    /** Mirrors {@code finances_ai.models.BacktestRequest}. Decimals go as strings. */
    public record WireBacktestRequest(String strategy, Map<String, String> params, String symbol,
                                      String timeframe, LocalDate start, LocalDate end,
                                      @JsonProperty("initial_cash") String initialCash,
                                      @JsonProperty("slippage_bps") String slippageBps,
                                      @JsonProperty("commission_per_order") String commissionPerOrder,
                                      @JsonProperty("out_of_sample_fraction") String outOfSampleFraction) {
    }

    /** Mirrors {@code finances_ai.models.EvaluateRequest}. */
    public record WireEvaluateRequest(String strategy, Map<String, String> params, String symbol,
                                      String timeframe, String position,
                                      @JsonProperty("lookback_bars") int lookbackBars) {
    }

    /** Mirrors {@code finances_ai.models.EvaluateResult}. */
    public record WireEvaluateResult(String provider, String symbol, int bars,
                                     @JsonProperty("as_of") Instant asOf,
                                     @JsonProperty("last_close") BigDecimal lastClose,
                                     String action, String reason, List<String> warnings) {
        public List<String> safeWarnings() {
            return warnings == null ? List.of() : warnings;
        }
    }

    /** The result whole, plus the figures a list needs, read from it. */
    public record Backtest(String raw, JsonNode tree, String provider, int bars, int trades, int dayTrades,
                           BigDecimal totalReturnPct, BigDecimal benchmarkReturnPct,
                           BigDecimal maxDrawdownPct, BigDecimal sharpe, BigDecimal finalEquity,
                           BigDecimal inSampleReturnPct, BigDecimal outOfSampleReturnPct,
                           List<String> warnings) {

        public static Backtest of(String raw) {
            JsonNode tree = JSON.readTree(raw);
            JsonNode metrics = tree.path("metrics");
            if (metrics.isMissingNode()) {
                throw new AiServiceClient.AiServiceException("The backtest result had no metrics.");
            }
            var warnings = new java.util.ArrayList<String>();
            for (JsonNode w : tree.path("warnings")) {
                warnings.add(w.asString());
            }
            return new Backtest(raw, tree, tree.path("provider").asString(),
                metrics.path("bars").asInt(), metrics.path("trades").asInt(),
                metrics.path("day_trades").asInt(),
                decimal(metrics.path("total_return_pct")), decimal(metrics.path("benchmark_return_pct")),
                decimal(metrics.path("max_drawdown_pct")), decimal(metrics.path("sharpe")),
                decimal(metrics.path("final_equity")),
                decimal(tree.path("in_sample").path("total_return_pct")),
                decimal(tree.path("out_of_sample").path("total_return_pct")),
                warnings);
        }

        static BigDecimal decimal(JsonNode node) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }
            if (node.isNumber()) {
                return node.decimalValue();
            }
            try {
                return new BigDecimal(node.asString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
