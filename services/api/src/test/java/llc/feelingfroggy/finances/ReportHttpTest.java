package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** The reports and the checkpoint delete, as the browser and the MCP tools drive them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Reports over HTTP")
class ReportHttpTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;

    private ApiClient api;
    private long userId;
    private long checkingId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        userId = jdbc.queryForObject("SELECT id FROM app_user", Long.class);
        long entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        checkingId = api.postJson("/api/v1/accounts", Map.of("name", "Checking",
            "accountType", "CHECKING", "ledgerEntityId", entityId)).id();
    }

    @Test
    @DisplayName("a checkpoint can be removed, and is gone from the reconciliation report")
    void aCheckpointCanBeRemoved() {
        jdbc.update("""
            INSERT INTO statement (user_id, account_id, period_start, period_end, closing_balance)
            VALUES (?, ?, '2026-08-01', '2026-08-31', 123.45)
            """, userId, checkingId);
        var before = api.get("/api/v1/reports/reconciliation").json();
        assertThat(before).hasSize(1);
        long statementId = before.get(0).get("statementId").asLong();

        assertThat(api.delete("/api/v1/statements/" + statementId).status()).isEqualTo(204);

        assertThat(api.get("/api/v1/reports/reconciliation").json()).isEmpty();
        assertThat(api.delete("/api/v1/statements/" + statementId).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("monthly totals say how much of the month is still uncategorized")
    void monthlyTotalsOverHttp() {
        api.postJson("/api/v1/transactions", Map.of("accountId", checkingId,
            "transactionDate", "2026-08-14", "amount", "84.31", "direction", "debit",
            "description", "KROGER"));
        api.postJson("/api/v1/transactions", Map.of("accountId", checkingId,
            "transactionDate", "2026-08-15", "amount", "40.00", "direction", "debit",
            "description", "SOMEWHERE NEW"));

        var rows = api.get("/api/v1/reports/monthly-totals?from=2026-08-01&to=2026-08-31").json();

        // Combined row first (null entity), then the entity's own.
        assertThat(rows).hasSize(2);
        var combined = rows.get(0);
        assertThat(combined.get("ledgerEntityId").isNull()).isTrue();
        assertThat(new BigDecimal(combined.get("moneyOut").asText())).isEqualByComparingTo("124.31");
        assertThat(new BigDecimal(combined.get("uncategorizedOut").asText()))
            .isEqualByComparingTo("124.31");
        assertThat(combined.get("uncategorizedCount").asInt()).isEqualTo(2);
    }
}
