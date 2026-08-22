package llc.feelingfroggy.finances.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the internal Python AI service.
 *
 * @param baseUrl base URL of the AI service; internal network only, never browser-reachable
 * @param timeout per-request timeout
 */
@ConfigurationProperties(prefix = "finances.ai")
public record AiServiceProperties(String baseUrl, Duration timeout) {

    public AiServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8000";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(30);
        }
    }
}
