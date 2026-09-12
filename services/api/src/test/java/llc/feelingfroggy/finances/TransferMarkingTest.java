package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * A transfer that was not one. Review 2026-09-11, P8: "TRANSFER TO PLUMBER JOE" was a transfer by
 * rule, the CHECK constraint then kept it uncategorizable, and the API had no way back.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Marking and un-marking a transfer")
class TransferMarkingTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;

    private ApiClient api;
    private long checkingId;
    private long savingsId;
    private long groceriesId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        long entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        checkingId = api.postJson("/api/v1/accounts", Map.of("name", "Checking",
            "accountType", "CHECKING", "ledgerEntityId", entityId)).id();
        savingsId = api.postJson("/api/v1/accounts", Map.of("name", "Savings",
            "accountType", "SAVINGS", "ledgerEntityId", entityId)).id();
        groceriesId = api.postJson("/api/v1/categories",
            Map.of("name", "Groceries", "kind", "EXPENSE")).id();
    }

    private long purchase(String description) {
        return api.postJson("/api/v1/transactions", Map.of("accountId", checkingId,
            "transactionDate", "2026-08-14", "amount", "250.00", "direction", "debit",
            "description", description, "categoryId", groceriesId)).json().get(0).get("id").asLong();
    }

    @Test
    @DisplayName("a row wrongly called a transfer can be made spending again, and categorized")
    void aSingleSidedTransferCanBeUnmarked() {
        long id = purchase("ZELLE TRANSFER TO PLUMBER JOE");
        var marked = api.putJson("/api/v1/transactions/" + id + "/transfer", Map.of("transfer", true));
        assertThat(marked.status()).isEqualTo(200);
        assertThat(marked.json().get("transfer").asBoolean()).isTrue();
        // Marking cleared the category: a transfer is never spending.
        assertThat(marked.json().get("categoryId").isNull()).isTrue();

        var unmarked = api.putJson("/api/v1/transactions/" + id + "/transfer",
            Map.of("transfer", false));

        assertThat(unmarked.status()).isEqualTo(200);
        assertThat(unmarked.json().get("transfer").asBoolean()).isFalse();
        // Back in the review queue, and bookable against what it really was.
        var review = api.get("/api/v1/transactions/review").json().get("content");
        assertThat(review).hasSize(1);
        var categorized = api.putJson("/api/v1/transactions/" + id + "/category",
            Map.of("categoryId", groceriesId));
        assertThat(categorized.status()).isEqualTo(200);
        assertThat(categorized.json().get("categoryId").asLong()).isEqualTo(groceriesId);
    }

    @Test
    @DisplayName("one leg of a two-sided transfer cannot be un-marked on its own")
    void aLegOfAManualTransferIsRefused() {
        var legs = api.postJson("/api/v1/transactions", Map.of("accountId", checkingId,
            "transactionDate", "2026-08-14", "amount", "500.00", "direction", "debit",
            "description", "To savings", "transfer", true, "transferAccountId", savingsId)).json();
        assertThat(legs).hasSize(2);
        long leg = legs.get(0).get("id").asLong();

        var refused = api.putJson("/api/v1/transactions/" + leg + "/transfer",
            Map.of("transfer", false));

        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.body()).contains("two-sided transfer");
        // Nothing moved: both legs are still transfers.
        Integer transfers = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE is_transfer", Integer.class);
        assertThat(transfers).isEqualTo(2);
    }

    @Test
    @DisplayName("an unknown row is a 404, not a guess")
    void unknownRowIsNotFound() {
        assertThat(api.putJson("/api/v1/transactions/999999/transfer", Map.of("transfer", false))
            .status()).isEqualTo(404);
    }
}
