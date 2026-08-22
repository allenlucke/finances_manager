package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * No database, no Spring context — this runs anywhere, including CI without Postgres.
 *
 * <p>Real integration tests arrive with M1. When they do, use Testcontainers against Postgres 18
 * rather than H2: this app leans on Postgres-specific SQL and an in-memory stand-in will lie.
 */
class AiServiceClientTest {

    @Test
    void reportsUnreachableWhenAiServiceIsDown() {
        var properties = new AiServiceProperties("http://localhost:59999", Duration.ofMillis(250));
        var client = new AiServiceClient(properties, RestClient.builder());

        assertThat(client.health()).isEqualTo("unreachable");
    }

    @Test
    void appliesDefaultsForBlankConfiguration() {
        var properties = new AiServiceProperties("  ", null);

        assertThat(properties.baseUrl()).isEqualTo("http://localhost:8000");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
