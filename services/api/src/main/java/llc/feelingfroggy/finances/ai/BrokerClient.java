package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * The wire to the broker, through the Python service (M7b).
 *
 * <p>This side never decides whether an order should go: the confirmation, the daily cap and the
 * kill switch are {@code OrderService}'s, checked against the database before this is called.
 * The broker's keys live in the Python container; the API holds none.
 */
@Component
public class BrokerClient {

    private final RestClient restClient;

    public BrokerClient(AiServiceProperties properties, RestClient.Builder builder) {
        var httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.timeout())
            .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    public WireBrokerStatus status() {
        try {
            var status = restClient.get().uri("/broker/status").retrieve().body(WireBrokerStatus.class);
            return status == null
                ? new WireBrokerStatus("unknown", false, true, null, null, null, "No answer from the service.")
                : status;
        } catch (Exception e) {
            return new WireBrokerStatus("unreachable", false, true, null, null, null,
                "The broker service could not be reached.");
        }
    }

    public WireOrder submit(OrderRequest order) {
        return call(() -> restClient.post().uri("/broker/orders").body(order).retrieve().body(WireOrder.class));
    }

    public WireOrder lookup(String brokerOrderId) {
        return call(() -> restClient.get().uri("/broker/orders/{id}", brokerOrderId).retrieve().body(WireOrder.class));
    }

    public WireOrder cancel(String brokerOrderId) {
        return call(() -> restClient.delete().uri("/broker/orders/{id}", brokerOrderId).retrieve().body(WireOrder.class));
    }

    private WireOrder call(java.util.function.Supplier<WireOrder> request) {
        try {
            var result = request.get();
            if (result == null) {
                throw new AiServiceClient.AiServiceException("The broker service returned nothing.");
            }
            return result;
        } catch (RestClientResponseException e) {
            String detail = AiServiceClient.detailOf(e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 503) {
                throw new BrokerOff(detail == null ? "No broker is configured." : detail);
            }
            throw new AiServiceClient.AiServiceException(
                detail == null ? "The broker refused the request." : detail, e);
        } catch (AiServiceClient.AiServiceException | BrokerOff e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceClient.AiServiceException("The broker service could not be reached.", e);
        }
    }

    /** No broker configured: trading is off at the wire. A state, not a fault. */
    public static class BrokerOff extends RuntimeException {
        public BrokerOff(String message) {
            super(message);
        }
    }

    /** Mirrors {@code finances_ai.models.OrderRequest}. Decimals go as strings. */
    public record OrderRequest(@JsonProperty("client_order_id") String clientOrderId, String symbol,
                               String side, String quantity, @JsonProperty("order_type") String orderType,
                               @JsonProperty("limit_price") String limitPrice,
                               @JsonProperty("time_in_force") String timeInForce) {
    }

    /** Mirrors {@code finances_ai.models.BrokerOrder}. */
    public record WireOrder(String broker, @JsonProperty("broker_order_id") String brokerOrderId,
                            String status, @JsonProperty("broker_status") String brokerStatus,
                            @JsonProperty("submitted_at") Instant submittedAt,
                            @JsonProperty("filled_at") Instant filledAt,
                            @JsonProperty("filled_quantity") BigDecimal filledQuantity,
                            @JsonProperty("filled_avg_price") BigDecimal filledAvgPrice) {
    }

    /** Mirrors {@code finances_ai.models.BrokerStatus}. */
    public record WireBrokerStatus(String broker, boolean available, boolean paper,
                                   @JsonProperty("market_open") Boolean marketOpen,
                                   @JsonProperty("buying_power") BigDecimal buyingPower,
                                   @JsonProperty("portfolio_value") BigDecimal portfolioValue,
                                   String detail) {
    }
}
