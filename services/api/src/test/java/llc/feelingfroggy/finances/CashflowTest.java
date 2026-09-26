package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import llc.feelingfroggy.finances.service.Notifier;
import llc.feelingfroggy.finances.service.RecurringService;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The ledger's rhythm (M9, D-21): series found with their evidence, and net worth kept by day. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Cash flow, recurring charges and net worth history")
class CashflowTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private Notifier notifier;
    @LocalServerPort private int port;

    private ApiClient api;
    private long checking;
    private final LocalDate today = LocalDate.now(ZoneId.of("America/Chicago"));

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        long entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        checking = api.postJson("/api/v1/accounts", Map.of("ledgerEntityId", entityId, "name", "Everyday checking",
            "accountType", "checking")).json().get("id").asLong();
    }

    private void debit(LocalDate on, String amount, String description) {
        var r = api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", on.toString(),
            "amount", amount, "direction", "debit", "description", description));
        assertThat(r.status()).as(r.body()).isEqualTo(201);
    }

    @Test
    @DisplayName("the merchant key ignores digits, punctuation and store numbers")
    void merchantKey() {
        assertThat(RecurringService.merchantKey(null, "NETFLIX.COM 866-579-7172 CA")).isEqualTo("NETFLIX COM CA");
        assertThat(RecurringService.merchantKey(null, "AMAZON MKTPL*2K3J4 Amzn.com/bill")).isEqualTo("AMAZON MKTPL AMZN");
        assertThat(RecurringService.merchantKey("Spotify", "SPOTIFY USA 123")).isEqualTo("SPOTIFY");
        assertThat(RecurringService.cadenceOf(30)).isEqualTo("monthly");
        assertThat(RecurringService.cadenceOf(14)).isEqualTo("biweekly");
        assertThat(RecurringService.cadenceOf(45)).isNull();
    }

    @Test
    @DisplayName("a steady monthly charge is a series; one that stopped is missing; a spike and a double charge are anomalies")
    void seriesAndAnomalies() {
        // Netflix on the 15th for six months, the last one this month or last.
        LocalDate anchor = today.withDayOfMonth(15);
        if (anchor.isAfter(today)) {
            anchor = anchor.minusMonths(1);
        }
        for (int m = 5; m >= 0; m--) {
            debit(anchor.minusMonths(m), "15.49", "NETFLIX.COM 866-579-7172 CA");
        }
        // A gym charge on the 3rd that stopped three months ago.
        LocalDate gym = today.withDayOfMonth(3).minusMonths(3);
        for (int m = 4; m >= 0; m--) {
            debit(gym.minusMonths(m), "40.00", "PLANET FITNESS CLUB 0412");
        }
        // Electric: five normal bills then a spike.
        LocalDate power = today.minusDays(6);
        for (int m = 5; m >= 1; m--) {
            debit(power.minusMonths(m), "120.00", "CITY POWER & LIGHT");
        }
        debit(power, "310.00", "CITY POWER & LIGHT");
        // A coffee charged again the next day for the same amount. (The same day would be refused by
        // the dedupe rule before it ever reached the ledger, which is the right first line of defence.)
        debit(today.minusDays(2), "6.25", "CORNER COFFEE 118");
        debit(today.minusDays(1), "6.25", "CORNER COFFEE 118");

        var report = api.get("/api/v1/cashflow?days=30").json();

        var byLabel = new java.util.HashMap<String, tools.jackson.databind.JsonNode>();
        report.get("series").forEach(s -> byLabel.put(s.get("label").asText(), s));
        var netflix = byLabel.get("NETFLIX.COM 866-579-7172 CA");
        assertThat(netflix).as(report.toString()).isNotNull();
        assertThat(netflix.get("cadence").asText()).isEqualTo("monthly");
        assertThat(new BigDecimal(netflix.get("typicalAmount").asText())).isEqualByComparingTo("15.49");
        assertThat(netflix.get("occurrences").asInt()).isEqualTo(6);
        assertThat(netflix.get("amountVaries").asBoolean()).isFalse();
        assertThat(netflix.get("nextExpected").asText()).isEqualTo(anchor.plusMonths(1).toString());
        assertThat(netflix.get("status").asText()).isIn("upcoming", "on track");

        var gymSeries = byLabel.get("PLANET FITNESS CLUB 0412");
        assertThat(gymSeries.get("status").asText()).isEqualTo("missing");

        var powerSeries = byLabel.get("CITY POWER & LIGHT");
        assertThat(powerSeries.get("amountVaries").asBoolean()).isTrue();
        assertThat(new BigDecimal(powerSeries.get("typicalAmount").asText())).isEqualByComparingTo("120.00");

        var anomalies = report.get("anomalies");
        assertThat(anomalies).extracting(a -> a.get("kind").asText()).contains("duplicate", "unusual_amount");
        assertThat(anomalies.toString()).contains("Possible duplicate: CORNER COFFEE 118 for $6.25");
        assertThat(anomalies.toString()).contains("CITY POWER & LIGHT was $310.00").contains("usually about $120.00");

        // The forecast carries Netflix (and power) into the next 30 days; the missing gym is left out.
        var upcoming = report.get("upcoming");
        assertThat(upcoming.toString()).contains("NETFLIX");
        assertThat(upcoming.toString()).doesNotContain("PLANET FITNESS");
        assertThat(new BigDecimal(report.get("expectedOut").asText())).isGreaterThan(BigDecimal.ZERO);

        // The digest says the same things, and summarises the week ahead only when something is due in it.
        var digest = api.get("/api/v1/digest/preview").json().toString();
        assertThat(digest).contains("PLANET FITNESS CLUB 0412 ($40.00 monthly) has not appeared since");
        assertThat(digest).contains("Possible duplicate");
        boolean dueThisWeek = !api.get("/api/v1/cashflow?days=7").json().get("upcoming").isEmpty();
        assertThat(digest.contains("expected in the next 7 days")).isEqualTo(dueThisWeek);
    }

    @Test
    @DisplayName("a snapshot keeps today's net worth once, and history returns it")
    void netWorthHistory() {
        debit(today, "25.00", "Lunch");
        assertThat(api.get("/api/v1/reports/net-worth/history").json()).isEmpty();

        api.postJson("/api/v1/reports/net-worth/snapshot", Map.of());
        api.postJson("/api/v1/reports/net-worth/snapshot", Map.of());

        var history = api.get("/api/v1/reports/net-worth/history?days=30").json();
        // One combined row and one per set of books, for today, taken twice but kept once.
        assertThat(history).hasSize(2);
        var combined = history.get(0);
        assertThat(combined.get("ledgerEntityId").isNull()).isTrue();
        assertThat(combined.get("asOf").asText()).isEqualTo(today.toString());
        assertThat(new BigDecimal(combined.get("netWorth").asText())).isEqualByComparingTo("-25.00");
    }
}
