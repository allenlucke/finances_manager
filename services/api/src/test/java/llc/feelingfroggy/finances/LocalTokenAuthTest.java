package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import llc.feelingfroggy.finances.security.LocalTokenProperties;
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
 * The local automation token (D-17) — what it grants, and what it must not.
 *
 * <p>This is an authentication bypass by design, so the tests that matter most are the negative
 * ones. A bypass whose limits are untested is indistinguishable from an open door.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "finances.local-token.value=" + LocalTokenAuthTest.TOKEN)
@DisplayName("Local automation token")
class LocalTokenAuthTest extends PostgresIntegrationTest {

    /** Long enough to satisfy the minimum-length rule; otherwise the context would not start. */
    static final String TOKEN = "test-token-that-is-long-enough-to-be-accepted";

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private ApiClient api;

    @BeforeEach
    void reset() {
        cleanDatabase(jdbc);

        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
    }

    /** A fresh client with no cookies at all — the state the MCP server is actually in. */
    private ApiClient tokenOnly(String token) {
        return new ApiClient(port).bearer(token);
    }

    @Test
    @DisplayName("the right token authenticates with no session cookie of any kind")
    void tokenAuthenticatesWithoutASession() {
        var mcp = tokenOnly(TOKEN);

        var me = mcp.get("/api/v1/auth/me");

        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("email").asText()).isEqualToIgnoringCase("owner@finances.invalid");
        // The point of the whole exercise: no login happened and no cookie was stored.
        assertThat(mcp.hasSessionCookie()).isFalse();
    }

    @Test
    @DisplayName("a wrong token is refused")
    void wrongTokenIsRefused() {
        assertThat(tokenOnly("wrong-token-of-exactly-the-right-sort-of-length")
            .get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("a token that is a prefix of the real one is refused")
    void prefixIsNotEnough() {
        assertThat(tokenOnly(TOKEN.substring(0, TOKEN.length() - 1))
            .get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("no token at all is still refused")
    void anonymousIsStillRefused() {
        assertThat(new ApiClient(port).get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("the token can write without a CSRF token, which a cookie session cannot")
    void tokenWritesWithoutCsrf() {
        var mcp = tokenOnly(TOKEN);
        long entityId = mcp.get("/api/v1/entities").json().get(0).get("id").asLong();

        // No primeCsrf() and no XSRF cookie in this client's jar, so nothing supplies the header.
        var created = mcp.postJson("/api/v1/accounts",
            Map.of("name", "Checking", "accountType", "CHECKING", "ledgerEntityId", entityId));

        assertThat(created.status()).isEqualTo(201);
    }

    @Test
    @DisplayName("X-Forwarded-For cannot be used to fake a loopback connection")
    void forwardedHeaderIsNotTrusted() {
        // The connection really is loopback here, so this cannot prove a remote caller is blocked.
        // What it does prove is that the header plays no part in the decision — if the filter
        // consulted it, claiming a routable address would flip the answer. It does not.
        var mcp = tokenOnly(TOKEN).header("X-Forwarded-For", "203.0.113.7");

        assertThat(mcp.get("/api/v1/auth/me").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("the token still works once a passkey makes MFA required")
    void tokenSatisfiesTheSecondFactor() {
        // The documented concession (D-12/D-17): registering a passkey does not shut out a caller
        // holding this token. Pinned so it is a decision on record rather than a surprise.
        jdbc.update("INSERT INTO user_entities (id, name, display_name) VALUES (?, ?, ?)",
            "dXNlci1oYW5kbGUtMQ", "owner@finances.invalid", "Owner");
        jdbc.update("""
            INSERT INTO user_credentials
              (credential_id, user_entity_user_id, public_key, signature_count, uv_initialized,
               backup_eligible, authenticator_transports, public_key_credential_type,
               backup_state, created, last_used, label)
            VALUES (?, ?, ?, 0, TRUE, TRUE, 'internal,hybrid', 'public-key', TRUE, now(), now(), ?)
            """, "Y3JlZGVudGlhbC0x", "dXNlci1oYW5kbGUtMQ", new byte[] {1, 2, 3}, "Test key");

        // A password-only cookie session is now refused, and specifically for want of the second
        // factor — 401 carrying factor_required, which is what makes the SPA start the WebAuthn
        // ceremony rather than ask for the passphrase again.
        var browser = new ApiClient(port);
        browser.primeCsrf();
        browser.login("owner@finances.invalid", "a-long-enough-passphrase");
        var refused = browser.get("/api/v1/accounts");
        assertThat(refused.status()).isEqualTo(401);
        assertThat(refused.body()).contains("factor_required").contains("webauthn");

        // ...while the token, which asserts the webauthn factor, sails through.
        assertThat(tokenOnly(TOKEN).get("/api/v1/accounts").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("a controller's 404 stays a 404 rather than becoming a 401")
    void errorsAreNotReportedAsAuthenticationFailures() {
        var mcp = tokenOnly(TOKEN);

        var missing = mcp.get("/api/v1/accounts/999999");

        // Spring re-dispatches errors through the filter chain, and OncePerRequestFilter skips
        // that pass by default — so this used to answer 401, sending the caller off to re-check a
        // token that was never wrong. Any 4xx would do here; the point is that it is not 401.
        assertThat(missing.status()).isEqualTo(404);
    }

    @Test
    @DisplayName("a token shorter than the minimum is refused at construction, not at runtime")
    void shortTokensAreRejectedEarly() {
        // Failing at startup is the whole point: a weak token that is quietly accepted looks
        // exactly like protection while providing almost none.
        assertThatThrownBy(() -> new LocalTokenProperties("too-short", true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least " + LocalTokenProperties.MINIMUM_LENGTH);

        assertThat(new LocalTokenProperties("   ", true).enabled()).isFalse();
        assertThat(new LocalTokenProperties(null, true).enabled()).isFalse();
    }
}
