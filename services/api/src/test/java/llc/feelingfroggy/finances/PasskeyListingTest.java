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
 * Listing and removing passkeys once one actually exists.
 *
 * <p>The listing used to read its TIMESTAMPTZ columns as {@code Instant}, which the Postgres
 * driver refuses — so the mapper threw on the first row, and the screen showed "could not check
 * which passkeys are registered" the moment there was one to show. With an empty table the mapper
 * never ran and every earlier test passed. The browser suite's passkey journey found it.
 *
 * <p>Authenticates with the local token because it asserts both factors: a password-only session
 * is refused for this endpoint as soon as a credential exists, which is correct and is exactly why
 * the listing had never been exercised with a row present.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "finances.local-token.value=" + PasskeyListingTest.TOKEN)
@DisplayName("Passkey listing with a registered credential")
class PasskeyListingTest extends PostgresIntegrationTest {

    static final String TOKEN = "test-token-that-is-long-enough-to-be-accepted";
    private static final String EMAIL = "allen@feelingfroggy.llc";

    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        var setup = new ApiClient(port);
        setup.primeCsrf();
        setup.postJson("/api/v1/setup", Map.of("email", EMAIL,
            "displayName", "Allen", "password", "a-long-enough-passphrase"));
        api = new ApiClient(port).bearer(TOKEN);
    }

    /** The rows Spring Security's JDBC repositories write on registration, minimally. */
    private void registerCredential(String credentialId, String label) {
        jdbc.update("""
            INSERT INTO user_entities (id, name, display_name) VALUES (?, ?, 'Allen')
            ON CONFLICT (id) DO NOTHING
            """, "dXNlci1pZA", EMAIL);
        jdbc.update("""
            INSERT INTO user_credentials (credential_id, user_entity_user_id, public_key, label)
            VALUES (?, ?, ?, ?)
            """, credentialId, "dXNlci1pZA", new byte[] {1, 2, 3}, label);
    }

    @Test
    @DisplayName("a registered passkey is listed with its dates")
    void listsTheRegisteredPasskey() {
        registerCredential("Y3JlZC0x", "Virtual authenticator");

        var response = api.get("/api/v1/passkeys");

        assertThat(response.status()).isEqualTo(200);
        var rows = response.json();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("credentialId").asText()).isEqualTo("Y3JlZC0x");
        assertThat(rows.get(0).get("label").asText()).isEqualTo("Virtual authenticator");
        assertThat(rows.get(0).get("created").isNull()).isFalse();
        assertThat(rows.get(0).get("lastUsed").isNull()).isFalse();
        assertThat(rows.get(0).get("backedUp").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("removing it returns the account to passphrase only")
    void removalIsScopedAndReported() {
        registerCredential("Y3JlZC0x", "Virtual authenticator");

        assertThat(api.delete("/api/v1/passkeys/Y3JlZC0x").status()).isEqualTo(204);
        assertThat(api.get("/api/v1/passkeys").json()).isEmpty();
        // Gone means gone: a second removal is a 404, not a silent no-op.
        assertThat(api.delete("/api/v1/passkeys/Y3JlZC0x").status()).isEqualTo(404);
    }
}
