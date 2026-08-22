package llc.feelingfroggy.finances.api;

import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M0 scaffold endpoint: proves the API is up and can see both the database and the AI service.
 * Replace with real resources as M1 lands; this is not a permanent part of the API surface.
 */
@RestController
@RequestMapping("/api/v1")
public class StatusController {

    private final DataSource dataSource;
    private final AiServiceClient aiServiceClient;
    private final String version;

    public StatusController(
            DataSource dataSource,
            AiServiceClient aiServiceClient,
            @Value("${finances.version:0.1.0-SNAPSHOT}") String version) {
        this.dataSource = dataSource;
        this.aiServiceClient = aiServiceClient;
        this.version = version;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "service", "finances-api",
            "version", version,
            "time", Instant.now().toString(),
            "database", databaseStatus(),
            "aiService", aiServiceClient.health());
    }

    private String databaseStatus() {
        try (var connection = dataSource.getConnection()) {
            var metaData = connection.getMetaData();
            return "up (%s %s)".formatted(
                metaData.getDatabaseProductName(), metaData.getDatabaseProductVersion());
        } catch (Exception e) {
            return "unreachable";
        }
    }
}
