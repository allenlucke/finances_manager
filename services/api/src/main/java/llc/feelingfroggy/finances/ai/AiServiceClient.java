package llc.feelingfroggy.finances.ai;

import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the Python AI service.
 *
 * <p>The AI service is stateless and advisory: it never writes to the database and its output is
 * always re-validated by this service before anything is persisted. See docs/ARCHITECTURE.md.
 */
@Component
public class AiServiceClient {

    private final RestClient restClient;

    public AiServiceClient(AiServiceProperties properties, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    /** Returns the AI service's reported health, or {@code "unreachable"} if it is down. */
    public String health() {
        try {
            HealthResponse response = restClient.get()
                .uri("/health")
                .retrieve()
                .body(HealthResponse.class);
            return response == null ? "unknown" : response.status();
        } catch (Exception e) {
            return "unreachable";
        }
    }

    public record HealthResponse(String status, String service, String version) {}
}
