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

/** Covers the four gaps left open at the end of the first M1a pass. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Closed gaps")
class GapsClosedTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    /** Base64URL of "user-handle-1" / "credential-1" — see registerPasskeyFor. */
    private static final String USER_HANDLE = "dXNlci1oYW5kbGUtMQ";
    private static final String CREDENTIAL_ID = "Y3JlZGVudGlhbC0x";

    private ApiClient api;
    private long personalId;
    private long cardId;
    private long checkingId;

    @BeforeEach
    void reset() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
    }

    private void setupAndLogin() {
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        assertThat(api.login("owner@finances.invalid", "a-long-enough-passphrase").status())
            .isEqualTo(204);
        personalId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        cardId = api.postJson("/api/v1/accounts", Map.of("name", "Card",
            "accountType", "CREDIT_CARD", "ledgerEntityId", personalId)).id();
        checkingId = api.postJson("/api/v1/accounts", Map.of("name", "Checking",
            "accountType", "CHECKING", "ledgerEntityId", personalId)).id();
    }

    private BigDecimal balance(long accountId) {
        return jdbc.queryForObject("SELECT balance FROM v_account_balance WHERE account_id = ?",
            BigDecimal.class, accountId);
    }

    // ---------- gap 1: two-legged transfers ----------

    @Test
    @DisplayName("paying a card reduces what the card says you owe")
    void manualTransferWritesBothLegs() {
        setupAndLogin();
        // Spend on the card first, so there is a balance to pay down.
        api.postJson("/api/v1/transactions", Map.of("accountId", cardId,
            "transactionDate", "2026-08-14", "amount", "696.31", "direction", "DEBIT",
            "description", "Groceries"));
        api.postJson("/api/v1/transactions", Map.of("accountId", checkingId,
            "transactionDate", "2026-08-01", "amount", "3000.00", "direction", "CREDIT",
            "description", "Paycheck"));

        assertThat(balance(cardId)).isEqualByComparingTo("-696.31");

        var response = api.postJson("/api/v1/transactions", Map.of(
            "accountId", checkingId, "transactionDate", "2026-08-20", "amount", "696.31",
            "direction", "DEBIT", "description", "Payment Thank You",
            "transfer", true, "transferAccountId", cardId));

        assertThat(response.status()).isEqualTo(201);
        // Both legs come back.
        assertThat(response.json()).hasSize(2);

        // The card is paid off, and the money left checking.
        assertThat(balance(cardId)).isEqualByComparingTo("0");
        assertThat(balance(checkingId)).isEqualByComparingTo("2303.69");
    }

    @Test
    @DisplayName("both legs share a transfer group and neither is categorized")
    void transferLegsAreLinkedAndUncategorized() {
        setupAndLogin();
        var response = api.postJson("/api/v1/transactions", Map.of(
            "accountId", checkingId, "transactionDate", "2026-08-20", "amount", "100.00",
            "direction", "DEBIT", "description", "Payment",
            "transfer", true, "transferAccountId", cardId));

        var legs = response.json();
        String groupA = legs.get(0).get("transferGroupId").asText();
        String groupB = legs.get(1).get("transferGroupId").asText();

        assertThat(groupA).isEqualTo(groupB);
        assertThat(legs.get(0).get("categoryId").isNull()).isTrue();
        assertThat(legs.get(1).get("categoryId").isNull()).isTrue();
        // Opposite directions, which is what makes the pair net to zero.
        assertThat(legs.get(0).get("direction").asText())
            .isNotEqualTo(legs.get(1).get("direction").asText());
    }

    @Test
    @DisplayName("a transfer does not change net worth")
    void transferIsNetZero() {
        setupAndLogin();
        api.postJson("/api/v1/transactions", Map.of("accountId", checkingId,
            "transactionDate", "2026-08-01", "amount", "1000.00", "direction", "CREDIT",
            "description", "Paycheck"));

        BigDecimal before = jdbc.queryForObject(
            "SELECT net_worth FROM v_net_worth WHERE ledger_entity_id IS NULL", BigDecimal.class);

        api.postJson("/api/v1/transactions", Map.of("accountId", checkingId,
            "transactionDate", "2026-08-20", "amount", "400.00", "direction", "DEBIT",
            "description", "Moved to card", "transfer", true, "transferAccountId", cardId));

        BigDecimal after = jdbc.queryForObject(
            "SELECT net_worth FROM v_net_worth WHERE ledger_entity_id IS NULL", BigDecimal.class);

        // Moving money neither creates nor destroys it.
        assertThat(after).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("deleting one leg of a transfer deletes the other")
    void deletingATransferRemovesBothLegs() {
        setupAndLogin();
        var legs = api.postJson("/api/v1/transactions", Map.of(
            "accountId", checkingId, "transactionDate", "2026-08-20", "amount", "250.00",
            "direction", "DEBIT", "description", "Payment",
            "transfer", true, "transferAccountId", cardId)).json();

        long firstLeg = legs.get(0).get("id").asLong();
        assertThat(api.delete("/api/v1/transactions/" + firstLeg).status()).isEqualTo(204);

        // Neither side is left dangling.
        assertThat(balance(cardId)).isEqualByComparingTo("0");
        assertThat(balance(checkingId)).isEqualByComparingTo("0");
        Integer live = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE deleted_at IS NULL", Integer.class);
        assertThat(live).isZero();
    }

    @Test
    @DisplayName("a transfer without a destination account is rejected, not silently one-sided")
    void transferRequiresBothAccounts() {
        setupAndLogin();

        var response = api.postJson("/api/v1/transactions", Map.of(
            "accountId", checkingId, "transactionDate", "2026-08-20", "amount", "100.00",
            "direction", "DEBIT", "description", "Payment", "transfer", true));

        assertThat(response.status()).isEqualTo(400);
    }

    // ---------- gap 2: login lockout ----------

    @Test
    @DisplayName("repeated bad passwords lock the account, and the right password then still fails")
    void lockoutAfterRepeatedFailures() {
        setupAndLogin();
        var attacker = new ApiClient(port);
        attacker.primeCsrf();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(attacker.login("owner@finances.invalid", "wrong-" + attempt).status())
                .isEqualTo(401);
        }

        // Locked: even the correct passphrase is refused while the window is open — and reported
        // as a lockout (429) rather than as bad credentials, so the person knows to wait instead of
        // retrying and extending it.
        assertThat(attacker.login("owner@finances.invalid", "a-long-enough-passphrase").status())
            .isEqualTo(429);

        Integer failures = jdbc.queryForObject(
            "SELECT count(*) FROM login_attempt WHERE NOT successful", Integer.class);
        assertThat(failures).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("a junk X-Forwarded-For header cannot switch lockout off")
    void lockoutSurvivesAHostileForwardedHeader() {
        setupAndLogin();
        var attacker = new ApiClient(port).header("X-Forwarded-For", "nope");
        attacker.primeCsrf();

        // Reproduced 2026-08-29: the header value was cast to inet inside the audit insert, the
        // insert threw, the failure row was never written, and lockout — which counts rows —
        // never triggered. Five wrong passwords then the right one signed straight in.
        for (int i = 0; i < 5; i++) {
            assertThat(attacker.login("owner@finances.invalid", "wrong-passphrase-here").status())
                .isEqualTo(401);
        }
        assertThat(attacker.login("owner@finances.invalid", "a-long-enough-passphrase").status())
            .isEqualTo(429);

        // And every failure was recorded — the audit row is the whole mechanism. Six, not five:
        // the attempt refused *because* the account was locked is itself a failed attempt, and
        // counting it is what keeps the window sliding while someone keeps trying.
        Integer failures = jdbc.queryForObject(
            "SELECT count(*) FROM login_attempt WHERE NOT successful", Integer.class);
        assertThat(failures).isEqualTo(6);
    }

    @Test
    @DisplayName("a few failures short of the limit do not lock the account")
    void underTheLimitStillWorks() {
        setupAndLogin();
        var user = new ApiClient(port);
        user.primeCsrf();

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(user.login("owner@finances.invalid", "typo-" + attempt).status())
                .isEqualTo(401);
        }

        assertThat(user.login("owner@finances.invalid", "a-long-enough-passphrase").status())
            .isEqualTo(204);
    }

    @Test
    @DisplayName("a successful login clears the failure count")
    void successResetsTheCounter() {
        setupAndLogin();
        var user = new ApiClient(port);
        user.primeCsrf();

        for (int attempt = 0; attempt < 4; attempt++) {
            user.login("owner@finances.invalid", "typo-" + attempt);
        }
        assertThat(user.login("owner@finances.invalid", "a-long-enough-passphrase").status())
            .isEqualTo(204);

        // Four more failures must not tip it over, because the counter restarted.
        var again = new ApiClient(port);
        again.primeCsrf();
        for (int attempt = 0; attempt < 4; attempt++) {
            again.login("owner@finances.invalid", "typo-again-" + attempt);
        }
        assertThat(again.login("owner@finances.invalid", "a-long-enough-passphrase").status())
            .isEqualTo(204);
    }

    @Test
    @DisplayName("successful logins are recorded too, not just failures")
    void successesAreAudited() {
        setupAndLogin();

        Integer successes = jdbc.queryForObject(
            "SELECT count(*) FROM login_attempt WHERE successful", Integer.class);

        assertThat(successes).isPositive();
    }

    // ---------- gap 3: passkeys become a required second factor ----------

    /**
     * Registers a passkey by writing Spring Security's own rows. A real ceremony needs a browser
     * authenticator, which an integration test has no way to drive — but the authorization rule
     * being tested reads these tables, so the effect is identical.
     *
     * <p>The ids must be <strong>Base64URL</strong>: Spring Security stores WebAuthn byte arrays
     * encoded, and decodes these columns back into its {@code Bytes} type. Plain text here throws
     * "Last unit does not have enough valid bits" from the Base64 decoder.
     */
    private void registerPasskeyFor(String username) {
        jdbc.update("INSERT INTO user_entities (id, name, display_name) VALUES (?, ?, ?)",
            USER_HANDLE, username, "Owner");
        jdbc.update("""
            INSERT INTO user_credentials
              (credential_id, user_entity_user_id, public_key, signature_count, uv_initialized,
               backup_eligible, authenticator_transports, public_key_credential_type,
               backup_state, created, last_used, label)
            VALUES (?, ?, ?, 0, TRUE, TRUE, 'internal,hybrid', 'public-key', TRUE, now(), now(), ?)
            """, CREDENTIAL_ID, USER_HANDLE, new byte[] {1, 2, 3}, "MacBook Touch ID");
    }

    @Test
    @DisplayName("with no passkey registered, a password alone is enough")
    void singleFactorWhenNoPasskey() {
        setupAndLogin();

        assertThat(api.get("/api/v1/accounts").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("once a passkey exists, a password-only session is no longer authorized")
    void passkeyBecomesRequiredAutomatically() {
        setupAndLogin();
        assertThat(api.get("/api/v1/accounts").status()).isEqualTo(200);

        registerPasskeyFor("owner@finances.invalid");

        // Same session, no new login: the requirement is derived from the data, so it applies at
        // once. The answer is a status with a machine-readable reason, never a redirect — the SPA
        // must be able to tell "present your passkey" from "sign in".
        var response = api.get("/api/v1/accounts");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.location()).isNull();
        assertThat(response.body()).contains("factor_required").contains("webauthn");
    }

    @Test
    @DisplayName("removing the last passkey returns the account to single factor")
    void deletingTheLastPasskeyRestoresAccess() {
        setupAndLogin();
        registerPasskeyFor("owner@finances.invalid");
        assertThat(api.get("/api/v1/accounts").status()).isEqualTo(401);

        // The recovery path when every authenticator is lost.
        jdbc.update("DELETE FROM user_credentials");

        assertThat(api.get("/api/v1/accounts").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("passkey registration serves a session and refuses an anonymous caller")
    void passkeyRegistrationRequiresASession() {
        setupAndLogin();

        // Signed in with a passphrase and no passkey yet: enrolling the first one must work, or
        // two-factor could never be turned on at all.
        var mine = api.postJson("/webauthn/register/options", java.util.Map.of());
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.body()).contains("challenge");

        // The same call with no session must not be served. Attaching a credential to an account
        // you do not own would be a complete bypass.
        var stranger = new ApiClient(port);
        stranger.primeCsrf();
        var theirs = stranger.postJson("/webauthn/register/options", java.util.Map.of());

        assertThat(theirs.status()).isNotEqualTo(200);
        assertThat(theirs.body()).doesNotContain("challenge");
    }

    @Test
    @DisplayName("presenting a passkey stays reachable without a completed session")
    void passkeyAssertionIsReachable() {
        setupAndLogin();
        var stranger = new ApiClient(port);
        stranger.primeCsrf();

        // Must not be 401: a second factor you cannot reach until you are fully authorized is a
        // deadlock. Any non-401 answer proves the endpoint is not gated.
        assertThat(stranger.postJson("/webauthn/authenticate/options", java.util.Map.of()).status())
            .isNotEqualTo(401);
    }

    @Test
    @DisplayName("the passkey list is itself gated behind the second factor")
    void passkeyListIsGated() {
        setupAndLogin();
        registerPasskeyFor("owner@finances.invalid");

        // Protected like everything else: with a passkey registered and not presented, even the
        // passkey list is gated. Letting it through would be a small hole for no benefit.
        var response = api.get("/api/v1/passkeys");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body()).contains("factor_required");
    }
}
