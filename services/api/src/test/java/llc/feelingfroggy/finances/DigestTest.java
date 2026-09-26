package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.service.Notifier;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The app speaks up (M8, D-20): the same list feeds the dashboard and the push, in sentences. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Reminders and the digest")
class DigestTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private Notifier notifier;
    @LocalServerPort private int port;

    private ApiClient api;
    private long entityId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        when(notifier.configured()).thenReturn(true);
    }

    private long account(String name) {
        return api.postJson("/api/v1/accounts", Map.of("ledgerEntityId", entityId, "name", name,
            "accountType", "checking")).json().get("id").asLong();
    }

    @Test
    @DisplayName("an empty ledger has nothing to say, and a quiet day sends nothing")
    void quiet() {
        assertThat(api.get("/api/v1/digest/preview").json()).isEmpty();

        var run = api.postJson("/api/v1/digest/send", Map.of()).json();

        // A manual send always goes out, even when all is quiet: the person asked.
        assertThat(run.get("items").asInt()).isZero();
        assertThat(run.get("sent").asBoolean()).isTrue();
        verify(notifier).send("Finances: all quiet", "All quiet: nothing needs a look.");
        assertThat(api.get("/api/v1/digest/runs").json()).hasSize(1);
    }

    @Test
    @DisplayName("reminders come due within their lead time, and completing one advances or retires it")
    void reminders() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Chicago"));
        var taxes = api.postJson("/api/v1/reminders", Map.of("title", "Estimated taxes", "dueOn",
            today.plusDays(2).toString(), "cadence", "quarterly", "leadDays", 5, "amount", "1200"));
        assertThat(taxes.status()).as(taxes.body()).isEqualTo(201);
        long taxesId = taxes.json().get("id").asLong();
        long later = api.postJson("/api/v1/reminders", Map.of("title", "Renew the LLC", "dueOn",
            today.plusDays(40).toString(), "leadDays", 7)).json().get("id").asLong();
        long overdue = api.postJson("/api/v1/reminders", Map.of("title", "Pay the card", "dueOn",
            today.minusDays(3).toString())).json().get("id").asLong();

        var items = api.get("/api/v1/digest/preview").json();

        assertThat(items).extracting(i -> i.get("text").asText()).containsExactly(
            "Pay the card: overdue by 3 days",
            "Estimated taxes (1200.00): due in 2 days (" + today.plusDays(2) + ")");
        assertThat(items.get(0).get("severity").asText()).isEqualTo("high");
        assertThat(items.get(1).get("link").asText()).isEqualTo("/dashboard");

        var done = api.postJson("/api/v1/reminders/" + taxesId + "/done", Map.of()).json();
        assertThat(done.get("dueOn").asText()).isEqualTo(today.plusDays(2).plusMonths(3).toString());
        assertThat(done.get("active").asBoolean()).isTrue();
        var retired = api.postJson("/api/v1/reminders/" + overdue + "/done", Map.of()).json();
        assertThat(retired.get("active").asBoolean()).isFalse();
        assertThat(api.get("/api/v1/digest/preview").json()).isEmpty();
        api.delete("/api/v1/reminders/" + later);
        assertThat(api.get("/api/v1/reminders").json()).hasSize(2);
    }

    @Test
    @DisplayName("what the ledger knows becomes sentences: a stale account, a mismatch, an over-target month")
    void ledgerItems() {
        long checking = account("Everyday checking");
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Chicago"));
        // A statement from fifty days ago that the (empty) ledger cannot reproduce.
        jdbc.update("""
            INSERT INTO statement (user_id, account_id, period_start, period_end, opening_balance, closing_balance)
            VALUES ((SELECT id FROM app_user LIMIT 1), ?, ?, ?, 100.0000, 250.0000)
            """, checking, today.minusDays(80), today.minusDays(50));
        long groceries = api.postJson("/api/v1/categories", Map.of("name", "Groceries", "kind", "expense"))
            .json().get("id").asLong();
        api.postJson("/api/v1/targets", Map.of("categoryId", groceries, "ledgerEntityId", entityId,
            "amount", "100", "cadence", "monthly", "effectiveFrom", today.withDayOfMonth(1).toString()));
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", today.toString(),
            "amount", "160.00", "direction", "debit", "description", "Big shop", "categoryId", groceries));

        var items = api.get("/api/v1/digest/preview").json();

        assertThat(items).extracting(i -> i.get("text").asText()).containsExactly(
            "Everyday checking: the statement ending " + today.minusDays(50) + " disagrees with the ledger by $150.00",
            "Groceries is $60.00 over its $100.00 target this month",
            "Everyday checking: nothing imported since " + today.minusDays(50) + " (50 days)");
        assertThat(items).extracting(i -> i.get("link").asText()).containsExactly("/dashboard", "/budget", "/import");

        // The push says the same things, and the run remembers them.
        var run = api.postJson("/api/v1/digest/send", Map.of()).json();
        var message = ArgumentCaptor.forClass(String.class);
        verify(notifier).send(org.mockito.ArgumentMatchers.eq("Finances: 3 things need a look"), message.capture());
        assertThat(message.getValue()).contains("• Groceries is $60.00 over");
        assertThat(run.get("body").asText()).isEqualTo(message.getValue());

        // A longer patience makes the stale item go away; the mismatch does not care.
        api.putJson("/api/v1/digest/settings", Map.of("staleAfterDays", 90));
        assertThat(api.get("/api/v1/digest/preview").json()).hasSize(2);
        assertThat(api.get("/api/v1/digest/settings").json().get("staleAfterDays").asInt()).isEqualTo(90);
    }

    @Test
    @DisplayName("with no channel, the run says so instead of pretending")
    void noChannel() {
        when(notifier.configured()).thenReturn(false);
        api.postJson("/api/v1/reminders", Map.of("title", "Now", "dueOn", LocalDate.now().toString()));

        var run = api.postJson("/api/v1/digest/send", Map.of()).json();

        assertThat(run.get("sent").asBoolean()).isFalse();
        assertThat(run.get("deliveryError").asText()).contains("NTFY_URL");
        verify(notifier, never()).send(anyString(), anyString());
    }
}
