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
 * The whole stack over HTTP: first-run setup, password login, CSRF, session cookies, and the
 * domain rules that matter.
 *
 * <p>Runs against a real server and a real Postgres, so it exercises the security filter chain as
 * configured rather than a mock of it — which is the point, since most auth mistakes live in the
 * chain rather than in the controllers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("API flow")
class ApiFlowTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private ApiClient api;

    @BeforeEach
    void reset() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
    }

    private void setupAndLogin() {
        api.primeCsrf();
        var created = api.postJson("/api/v1/setup", Map.of(
            "email", "allen@feelingfroggy.llc",
            "displayName", "Allen",
            "password", "a-long-enough-passphrase"));
        assertThat(created.status()).isEqualTo(201);

        var login = api.login("allen@feelingfroggy.llc", "a-long-enough-passphrase");
        assertThat(login.status()).isEqualTo(204);
    }

    private long firstEntityId() {
        return api.get("/api/v1/entities").json().get(0).get("id").asLong();
    }

    // ---------- tests ----------

    @Test
    @DisplayName("an unauthenticated request gets 401, not a redirect to a login page")
    void unauthenticatedIsRejected() {
        assertThat(api.get("/api/v1/accounts").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("setup closes itself once the first user exists")
    void setupIsSingleUse() {
        setupAndLogin();

        var second = api.postJson("/api/v1/setup", Map.of(
            "email", "someone@else.com", "displayName", "Someone",
            "password", "another-long-passphrase"));

        assertThat(second.status()).isEqualTo(409);
    }

    @Test
    @DisplayName("a state-changing request without the CSRF header is refused")
    void csrfIsEnforced() {
        setupAndLogin();

        // Session cookie still sent, CSRF header omitted — exactly what a cross-site form does.
        var response = api.postJsonWithoutCsrf("/api/v1/entities",
            Map.of("name", "Sneaky", "kind", "personal"));

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    @DisplayName("setup seeds Personal and Feeling Froggy so accounts can be created immediately")
    void setupSeedsEntities() {
        setupAndLogin();

        var entities = api.get("/api/v1/entities").json();

        assertThat(entities).hasSize(2);
        assertThat(entities.toString()).contains("Personal").contains("Feeling Froggy LLC");
    }

    @Test
    @DisplayName("/me returns the signed-in user and never the password hash")
    void meOmitsCredentials() {
        setupAndLogin();

        var response = api.get("/api/v1/auth/me");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("allen@feelingfroggy.llc");
        assertThat(response.body()).doesNotContain("password", "passwordHash", "$2a$", "{bcrypt}");
    }

    @Test
    @DisplayName("logging in establishes a session cookie")
    void loginSetsSession() {
        setupAndLogin();

        assertThat(api.hasSessionCookie()).isTrue();
    }

    @Test
    @DisplayName("an account can be created and comes back with a zero balance")
    void createAccount() {
        setupAndLogin();

        var response = api.postJson("/api/v1/accounts", Map.of(
            "name", "Chase Sapphire", "accountType", "CREDIT_CARD",
            "ledgerEntityId", firstEntityId(), "mask", "1234"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("name").asText()).isEqualTo("Chase Sapphire");
        assertThat(new BigDecimal(response.json().get("balance").asText()))
            .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("authenticating rotates the session id")
    void loginRotatesTheSessionId() {
        setupAndLogin();
        String first = api.sessionCookie().orElseThrow();

        // Authenticate again on the existing session. A controller-based login does not get
        // formLogin's ChangeSessionIdAuthenticationStrategy for free; without an explicit
        // rotation the id fixed before authentication stays valid after it.
        assertThat(api.login("allen@feelingfroggy.llc", "a-long-enough-passphrase").status())
            .isEqualTo(204);
        String second = api.sessionCookie().orElseThrow();

        assertThat(second).isNotEqualTo(first);
        // And the rotated session is the live one — not a dangling cookie.
        assertThat(api.get("/api/v1/auth/me").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("logging out invalidates the session immediately")
    void logoutRevokesTheSession() {
        setupAndLogin();
        assertThat(api.get("/api/v1/auth/me").status()).isEqualTo(200);

        assertThat(api.postJson("/api/v1/auth/logout", Map.of()).status()).isEqualTo(204);

        // The whole reason D-12 chose sessions over JWTs: revocation is immediate.
        assertThat(api.get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("sessions are stored in Postgres, so a restart would not sign anyone out")
    void sessionIsPersisted() {
        setupAndLogin();

        Integer sessions = jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class);

        assertThat(sessions).isPositive();
    }

    @Test
    @DisplayName("categorizing a transfer is refused — the credit-card double-count rule, over HTTP")
    void transferCannotBeCategorized() {
        setupAndLogin();
        long entityId = firstEntityId();

        long cardId = api.postJson("/api/v1/accounts", Map.of(
            "name", "Card", "accountType", "CREDIT_CARD", "ledgerEntityId", entityId)).id();
        long checkingId = api.postJson("/api/v1/accounts", Map.of(
            "name", "Checking", "accountType", "CHECKING", "ledgerEntityId", entityId)).id();
        long categoryId = api.postJson("/api/v1/categories", Map.of(
            "name", "Groceries", "kind", "EXPENSE")).id();

        // A payment from checking to the card: a transfer, not an expense.
        var payment = api.postJson("/api/v1/transactions", Map.of(
            "accountId", checkingId,
            "transactionDate", "2026-08-20",
            "amount", "512.44",
            "direction", "DEBIT",
            "description", "Payment Thank You",
            "transfer", true,
            "transferAccountId", cardId));

        assertThat(payment.status()).isEqualTo(201);
        // A manual transfer writes both legs, so the response is an array.
        assertThat(payment.json()).hasSize(2);
        assertThat(payment.json().get(0).get("categoryId").isNull()).isTrue();
        assertThat(payment.json().get(0).get("transfer").asBoolean()).isTrue();

        // Categorizing it afterwards must fail: that would double-charge the budget.
        long paymentId = payment.json().get(0).get("id").asLong();
        var attempt = api.putJson("/api/v1/transactions/" + paymentId + "/category",
            Map.of("categoryId", categoryId));

        assertThat(attempt.status()).isEqualTo(422);
        assertThat(attempt.body()).contains("double");
    }

    @Test
    @DisplayName("a normal expense can be categorized")
    void expenseCanBeCategorized() {
        setupAndLogin();
        long entityId = firstEntityId();
        long cardId = api.postJson("/api/v1/accounts", Map.of(
            "name", "Card", "accountType", "CREDIT_CARD", "ledgerEntityId", entityId)).id();
        long categoryId = api.postJson("/api/v1/categories", Map.of(
            "name", "Groceries", "kind", "EXPENSE")).id();

        var purchase = api.postJson("/api/v1/transactions", Map.of(
            "accountId", cardId, "transactionDate", "2026-08-14",
            "amount", "84.31", "direction", "DEBIT", "description", "KROGER #4521",
            "categoryId", categoryId));

        assertThat(purchase.status()).isEqualTo(201);
        // A non-transfer is a single leg, still returned as a one-element array for consistency.
        assertThat(purchase.json()).hasSize(1);
        assertThat(purchase.json().get(0).get("categoryId").asLong()).isEqualTo(categoryId);
        // Sign convention: a debit contributes negatively.
        assertThat(new BigDecimal(purchase.json().get(0).get("signedAmount").asText()))
            .isEqualByComparingTo("-84.31");
    }

    @Test
    @DisplayName("re-setting a target closes the previous one instead of colliding")
    void settingATargetClosesThePreviousOne() {
        setupAndLogin();
        long entityId = firstEntityId();
        long categoryId = api.postJson("/api/v1/categories", Map.of(
            "name", "Groceries", "kind", "EXPENSE")).id();

        assertThat(api.postJson("/api/v1/targets", Map.of(
            "categoryId", categoryId, "ledgerEntityId", entityId,
            "amount", "800.00", "effectiveFrom", "2026-01-01")).status()).isEqualTo(201);

        assertThat(api.postJson("/api/v1/targets", Map.of(
            "categoryId", categoryId, "ledgerEntityId", entityId,
            "amount", "650.00", "effectiveFrom", "2026-08-01")).status()).isEqualTo(201);

        var targets = api.get("/api/v1/targets").json();

        // Two rows: the old one closed, the new one open. Budget history comes for free.
        assertThat(targets).hasSize(2);
        assertThat(targets.toString()).contains("\"effectiveTo\":\"2026-08-01\"");
    }

    @Test
    @DisplayName("net worth reflects entered transactions")
    void netWorthReport() {
        setupAndLogin();
        long entityId = firstEntityId();
        long checkingId = api.postJson("/api/v1/accounts", Map.of(
            "name", "Checking", "accountType", "CHECKING", "ledgerEntityId", entityId)).id();
        long cardId = api.postJson("/api/v1/accounts", Map.of(
            "name", "Card", "accountType", "CREDIT_CARD", "ledgerEntityId", entityId)).id();

        api.postJson("/api/v1/transactions", Map.of("accountId", checkingId,
            "transactionDate", "2026-08-01", "amount", "3000.00", "direction", "CREDIT",
            "description", "Paycheck"));
        api.postJson("/api/v1/transactions", Map.of("accountId", cardId,
            "transactionDate", "2026-08-05", "amount", "500.00", "direction", "DEBIT",
            "description", "Card purchase"));

        var rows = api.get("/api/v1/reports/net-worth").json();

        // The combined row is the one with a null entity.
        var combined = java.util.stream.StreamSupport.stream(rows.spliterator(), false)
            .filter(row -> row.get("ledgerEntityId").isNull())
            .findFirst()
            .orElseThrow();
        assertThat(new BigDecimal(combined.get("netWorth").asText()))
            .isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("deleting a transaction hides it but keeps its dedupe key claimed")
    void deleteIsSoft() {
        setupAndLogin();
        long entityId = firstEntityId();
        long accountId = api.postJson("/api/v1/accounts", Map.of(
            "name", "Checking", "accountType", "CHECKING", "ledgerEntityId", entityId)).id();

        long id = api.postJson("/api/v1/transactions", Map.of(
            "accountId", accountId, "transactionDate", "2026-08-14",
            "amount", "84.31", "direction", "DEBIT", "description", "KROGER"))
            .json().get(0).get("id").asLong();

        assertThat(api.delete("/api/v1/transactions/" + id).status()).isEqualTo(204);

        // Gone from the API...
        var listing = api.get("/api/v1/transactions?from=2026-08-01&to=2026-08-31");
        assertThat(listing.json().get("totalElements").asInt()).isZero();

        // ...but still on disk, so a re-import cannot resurrect it.
        Integer rows = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE deleted_at IS NOT NULL", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("enums are spoken as lowercase codes on the wire, in both directions")
    void enumsUseCodesOnTheWire() {
        setupAndLogin();
        long entityId = firstEntityId();

        // Lowercase is the canonical spelling — the one Postgres, the Python service and the
        // TypeScript client all use. The browser sends exactly this. Before @JsonValue, Jackson
        // expected the Java constant name and every transaction the UI created came back 400.
        var account = api.postJson("/api/v1/accounts", Map.of(
            "name", "Card", "accountType", "credit_card", "ledgerEntityId", entityId));
        assertThat(account.status()).isEqualTo(201);
        assertThat(account.json().get("accountType").asText()).isEqualTo("credit_card");

        var category = api.postJson("/api/v1/categories", Map.of(
            "name", "Groceries", "kind", "expense"));
        assertThat(category.status()).isEqualTo(201);
        assertThat(category.json().get("kind").asText()).isEqualTo("expense");

        var transaction = api.postJson("/api/v1/transactions", Map.of(
            "accountId", account.id(), "transactionDate", "2026-08-14",
            "amount", "84.31", "direction", "debit", "description", "KROGER"));
        assertThat(transaction.status()).isEqualTo(201);
        assertThat(transaction.json().get(0).get("direction").asText()).isEqualTo("debit");
    }

    @Test
    @DisplayName("the uppercase Java constant name is still understood")
    void uppercaseIsStillAccepted() {
        setupAndLogin();

        // Lenient in, canonical out. Rejecting the constant name would break any caller written
        // against the old behaviour for no benefit.
        var account = api.postJson("/api/v1/accounts", Map.of(
            "name", "Checking", "accountType", "CHECKING", "ledgerEntityId", firstEntityId()));

        assertThat(account.status()).isEqualTo(201);
        assertThat(account.json().get("accountType").asText()).isEqualTo("checking");
    }

    @Test
    @DisplayName("an unknown enum value is rejected rather than silently defaulted")
    void unknownEnumValueIsRejected() {
        setupAndLogin();

        var account = api.postJson("/api/v1/accounts", Map.of(
            "name", "Odd", "accountType", "crypto_wallet", "ledgerEntityId", firstEntityId()));

        // Defaulting would put a wrong type on an account holding real money.
        assertThat(account.status()).isBetween(400, 499);
    }

    @Test
    @DisplayName("a bad password is rejected")
    void wrongPasswordRejected() {
        setupAndLogin();
        var fresh = new ApiClient(port);
        fresh.primeCsrf();

        assertThat(fresh.login("allen@feelingfroggy.llc", "not-the-passphrase").status())
            .isEqualTo(401);
    }
}
