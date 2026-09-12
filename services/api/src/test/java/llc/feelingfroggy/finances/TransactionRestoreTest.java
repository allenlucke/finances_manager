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

/**
 * Undoing a delete (D-17).
 *
 * <p>Allen chose to let automation delete things. That is only a reasonable thing to allow because
 * deletion here is soft and reversible — so the reversal is the part that has to actually work,
 * and especially the transfer case, where deleting one leg removes two rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Restoring a deleted transaction")
class TransactionRestoreTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private ApiClient api;
    private long cardId;
    private long checkingId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);

        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");

        long entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        cardId = api.postJson("/api/v1/accounts", Map.of("name", "Card",
            "accountType", "CREDIT_CARD", "ledgerEntityId", entityId)).id();
        checkingId = api.postJson("/api/v1/accounts", Map.of("name", "Checking",
            "accountType", "CHECKING", "ledgerEntityId", entityId)).id();
    }

    private BigDecimal balance(long accountId) {
        return jdbc.queryForObject("SELECT balance FROM v_account_balance WHERE account_id = ?",
            BigDecimal.class, accountId);
    }

    private long recordPurchase() {
        return api.postJson("/api/v1/transactions", Map.of(
            "accountId", cardId,
            "transactionDate", "2026-08-14",
            "amount", "84.31",
            "direction", "debit",
            "description", "KROGER #4521")).json().get(0).get("id").asLong();
    }

    @Test
    @DisplayName("a deleted purchase comes back with its balance")
    void deleteThenRestore() {
        long id = recordPurchase();
        assertThat(balance(cardId)).isEqualByComparingTo("-84.31");

        assertThat(api.delete("/api/v1/transactions/" + id).status()).isEqualTo(204);
        assertThat(balance(cardId)).isEqualByComparingTo("0.00");

        var restored = api.postJson("/api/v1/transactions/" + id + "/restore", Map.of());

        assertThat(restored.status()).isEqualTo(200);
        assertThat(restored.json()).hasSize(1);
        // The balance is the real assertion: a row that is back in the table but still filtered
        // out of the reporting views has not actually been restored.
        assertThat(balance(cardId)).isEqualByComparingTo("-84.31");
    }

    @Test
    @DisplayName("deleting one leg of a transfer removes both, and restoring brings both back")
    void transferLegsTravelTogether() {
        var legs = api.postJson("/api/v1/transactions", Map.of(
            "accountId", checkingId,
            "transactionDate", "2026-08-14",
            "amount", "500.00",
            "direction", "debit",
            "description", "Payment to card",
            // Both are required: transferAccountId alone takes the ordinary single-sided path,
            // which the controller is explicit about.
            "transfer", true,
            "transferAccountId", cardId)).json();
        assertThat(legs).hasSize(2);
        long nearLeg = legs.get(0).get("id").asLong();

        assertThat(balance(checkingId)).isEqualByComparingTo("-500.00");
        assertThat(balance(cardId)).isEqualByComparingTo("500.00");

        api.delete("/api/v1/transactions/" + nearLeg);
        assertThat(balance(checkingId)).isEqualByComparingTo("0.00");
        assertThat(balance(cardId)).isEqualByComparingTo("0.00");

        var restored = api.postJson("/api/v1/transactions/" + nearLeg + "/restore", Map.of());

        // Both legs, or money would arrive in an account it never left.
        assertThat(restored.status()).isEqualTo(200);
        assertThat(restored.json()).hasSize(2);
        assertThat(balance(checkingId)).isEqualByComparingTo("-500.00");
        assertThat(balance(cardId)).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("deleted transactions are listed so there is something to restore from")
    void deletedRowsAreVisible() {
        long id = recordPurchase();
        assertThat(api.get("/api/v1/transactions/deleted").json()).isEmpty();

        api.delete("/api/v1/transactions/" + id);

        var deleted = api.get("/api/v1/transactions/deleted").json();
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).get("description").asText()).isEqualTo("KROGER #4521");
    }

    @Test
    @DisplayName("restoring something that was never deleted is a 404, not a silent no-op")
    void restoringALiveRowIsRefused() {
        long id = recordPurchase();

        assertThat(api.postJson("/api/v1/transactions/" + id + "/restore", Map.of()).status())
            .isEqualTo(404);
    }

    @Test
    @DisplayName("restoring twice is refused rather than duplicating anything")
    void restoringTwiceIsRefused() {
        long id = recordPurchase();
        api.delete("/api/v1/transactions/" + id);
        assertThat(api.postJson("/api/v1/transactions/" + id + "/restore", Map.of()).status())
            .isEqualTo(200);

        assertThat(api.postJson("/api/v1/transactions/" + id + "/restore", Map.of()).status())
            .isEqualTo(404);
        assertThat(balance(cardId)).isEqualByComparingTo("-84.31");
    }
}
