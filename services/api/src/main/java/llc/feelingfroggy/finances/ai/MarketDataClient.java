package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Quotes from the Python service's market-data provider (M7a).
 *
 * <p>The vendor key lives in the Python service, like the inference key will; this side holds no
 * market credentials and asks over the compose network. HTTP/1.1 is pinned for the same reason
 * {@link AiServiceClient} pins it.
 *
 * <p>A 503 from the service means no provider is configured. That is a state, not a fault: it is
 * reported as {@link MarketDataOff} so the scheduler can skip quietly and the screen can say
 * "market data is off" instead of "something went wrong".
 */
@Component
public class MarketDataClient {

    private final RestClient restClient;

    public MarketDataClient(AiServiceProperties properties, RestClient.Builder builder) {
        var httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.timeout())
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    public QuotesResponse quotes(List<String> symbols) {
        try {
            var response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/market/quotes")
                    .queryParam("symbols", String.join(",", symbols))
                    .build())
                .retrieve()
                .body(QuotesResponse.class);
            if (response == null) {
                throw new AiServiceClient.AiServiceException("The market-data service returned nothing.");
            }
            return response;
        } catch (RestClientResponseException e) {
            String detail = AiServiceClient.detailOf(e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 503) {
                throw new MarketDataOff(detail == null ? "Market data is not configured." : detail);
            }
            throw new AiServiceClient.AiServiceException(
                detail == null ? "The market-data service refused the request." : detail, e);
        } catch (AiServiceClient.AiServiceException | MarketDataOff e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceClient.AiServiceException("The market-data service could not be reached.", e);
        }
    }

    public MarketStatus status() {
        try {
            var status = restClient.get().uri("/market/status").retrieve().body(MarketStatus.class);
            return status == null ? new MarketStatus("unknown", false, "No answer from the service.") : status;
        } catch (Exception e) {
            return new MarketStatus("unreachable", false, "The market-data service could not be reached.");
        }
    }

    /** No provider configured. A state the screen names, not a fault the log shouts about. */
    public static class MarketDataOff extends RuntimeException {
        public MarketDataOff(String message) {
            super(message);
        }
    }

    public record QuotesResponse(String provider, List<WireQuote> quotes, List<String> warnings) {
        public List<String> safeWarnings() {
            return warnings == null ? List.of() : warnings;
        }
    }

    /** Mirrors {@code finances_ai.models.Quote} field for field. */
    public record WireQuote(String symbol, BigDecimal price,
                            @JsonProperty("previous_close") BigDecimal previousClose,
                            @JsonProperty("as_of") Instant asOf, String source) {
    }

    /** Mirrors {@code finances_ai.models.MarketStatus}. */
    public record MarketStatus(String provider, boolean available, String detail) {
    }
}
