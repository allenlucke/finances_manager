package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.ai.MarketDataClient.MarketStatus;
import llc.feelingfroggy.finances.ai.MarketDataClient.QuotesResponse;
import llc.feelingfroggy.finances.ai.MarketDataClient.WireQuote;
import llc.feelingfroggy.finances.domain.Holding;
import llc.feelingfroggy.finances.domain.Security;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.repo.HoldingRepository;
import llc.feelingfroggy.finances.repo.SecurityRepository;
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

/**
 * Watching the market (M7a), as the browser and the MCP tools drive it. The vendor is stubbed at
 * the client; everything on this side of it — storing, deduping, the alert edge detector, the
 * holdings-at-market view — is real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Watching the market")
class MarketTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SecurityRepository securities;
    @Autowired private HoldingRepository holdings;
    @Autowired private AccountRepository accounts;
    @MockitoBean private MarketDataClient marketData;
    @LocalServerPort private int port;

    private ApiClient api;
    private long userId;
    private long brokerageId;

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
        brokerageId = api.postJson("/api/v1/accounts", Map.of("name", "Brokerage",
            "accountType", "BROKERAGE", "ledgerEntityId", entityId)).id();
        when(marketData.status()).thenReturn(new MarketStatus("fake", true, null));
    }

    private static WireQuote quote(String symbol, String price, String previousClose, String asOf) {
        return new WireQuote(symbol, new BigDecimal(price),
            previousClose == null ? null : new BigDecimal(previousClose), Instant.parse(asOf), "fake");
    }

    private void vendorAnswers(WireQuote... quotes) {
        when(marketData.quotes(any())).thenReturn(new QuotesResponse("fake", List.of(quotes), List.of()));
    }

    private ApiClient.Response refresh() {
        return api.postJson("/api/v1/market/quotes/refresh", Map.of());
    }

    @Test
    @DisplayName("watching a symbol, refreshing, and reading the quote back with today's move")
    void watchRefreshRead() {
        var watched = api.postJson("/api/v1/market/watchlist", Map.of("symbol", " aapl ", "note", "maybe"));
        assertThat(watched.status()).isEqualTo(201);
        assertThat(watched.json().get("symbol").asText()).isEqualTo("AAPL");
        vendorAnswers(quote("AAPL", "189.30", "187.10", "2026-09-25T19:59:58Z"));

        var first = refresh();

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.json().get("fetched").asInt()).isEqualTo(1);
        assertThat(first.json().get("stored").asInt()).isEqualTo(1);
        var captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(marketData).quotes(captor.capture());
        assertThat(captor.getValue()).containsExactly("AAPL");

        var rows = api.get("/api/v1/market/quotes").json();
        assertThat(rows).hasSize(1);
        var aapl = rows.get(0);
        assertThat(new BigDecimal(aapl.get("price").asText())).isEqualByComparingTo("189.30");
        // (189.30 - 187.10) / 187.10 = 1.1758%
        assertThat(new BigDecimal(aapl.get("changePct").asText())).isEqualByComparingTo("1.1758");
        assertThat(aapl.get("source").asText()).isEqualTo("fake");
        assertThat(aapl.get("watched").asBoolean()).isTrue();
        assertThat(aapl.get("held").asBoolean()).isFalse();

        // Polling between trades re-reads the same last trade: one row per moment, not per poll.
        var again = refresh();
        assertThat(again.json().get("stored").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quote", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("held symbols are refreshed too, and a holding is valued at the quote beside its snapshot")
    void holdingsAtMarket() {
        var fund = securities.save(new Security(userId, "FXAIX", "Fidelity 500 Index", "mutual_fund", false));
        var cash = securities.save(new Security(userId, "SPAXX", "Government Money Market", "money_market", true));
        var account = accounts.findById(brokerageId).orElseThrow();
        var position = new Holding(userId, account, fund, LocalDate.of(2026, 8, 27), new BigDecimal("2000.00"));
        position.setQuantity(new BigDecimal("10"));
        position.setLastPrice(new BigDecimal("200.00"));
        holdings.save(position);
        holdings.save(new Holding(userId, account, cash, LocalDate.of(2026, 8, 27), new BigDecimal("900.00")));
        vendorAnswers(quote("FXAIX", "201.44", "200.90", "2026-09-25T20:00:00Z"));

        refresh();
        var captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(marketData).quotes(captor.capture());
        // The fund is asked for; the cash sweep is not — a dollar is worth a dollar.
        assertThat(captor.getValue()).containsExactly("FXAIX");

        var rows = api.get("/api/v1/market/holdings").json();
        assertThat(rows).hasSize(2);
        var fundRow = rows.get(0).get("symbol").asText().equals("FXAIX") ? rows.get(0) : rows.get(1);
        var cashRow = fundRow == rows.get(0) ? rows.get(1) : rows.get(0);
        assertThat(new BigDecimal(fundRow.get("snapshotValue").asText())).isEqualByComparingTo("2000.00");
        assertThat(new BigDecimal(fundRow.get("liveValue").asText())).isEqualByComparingTo("2014.40");
        assertThat(fundRow.get("quoteSource").asText()).isEqualTo("fake");
        assertThat(new BigDecimal(cashRow.get("liveValue").asText())).isEqualByComparingTo("900.00");
        assertThat(cashRow.get("livePrice").isNull()).isTrue();

        // The rule that matters: a quote never replaces the snapshot in the balance or net worth.
        assertThat(jdbc.queryForObject("SELECT balance FROM v_account_balance WHERE account_id = ?",
            BigDecimal.class, brokerageId)).isEqualByComparingTo("2900.00");
        assertThat(api.get("/api/v1/market/quotes").json().get(0).get("held").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("an alert fires once on the crossing, stays quiet while the condition holds, and re-arms")
    void alertFiresOnTheEdge() {
        var created = api.postJson("/api/v1/market/alerts", Map.of("symbol", "AAPL", "rule", "above",
            "threshold", "190", "note", "take profit"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().get("armed").asBoolean()).isTrue();

        vendorAnswers(quote("AAPL", "189.00", "187.10", "2026-09-25T15:00:00Z"));
        assertThat(refresh().json().get("alertsFired").asInt()).isZero();

        vendorAnswers(quote("AAPL", "191.00", "187.10", "2026-09-25T15:05:00Z"));
        assertThat(refresh().json().get("alertsFired").asInt()).isEqualTo(1);
        // Still above: quiet.
        vendorAnswers(quote("AAPL", "192.00", "187.10", "2026-09-25T15:10:00Z"));
        assertThat(refresh().json().get("alertsFired").asInt()).isZero();
        // Back below: re-armed, no event.
        vendorAnswers(quote("AAPL", "185.00", "187.10", "2026-09-25T15:15:00Z"));
        assertThat(refresh().json().get("alertsFired").asInt()).isZero();
        assertThat(api.get("/api/v1/market/alerts").json().get(0).get("armed").asBoolean()).isTrue();
        // Crosses again: fires again.
        vendorAnswers(quote("AAPL", "195.00", "187.10", "2026-09-25T15:20:00Z"));
        assertThat(refresh().json().get("alertsFired").asInt()).isEqualTo(1);

        var events = api.get("/api/v1/market/alerts/events").json();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("message").asText()).isEqualTo("AAPL is above 190: 195");
        assertThat(new BigDecimal(events.get(0).get("price").asText())).isEqualByComparingTo("195");
        // No channel configured in tests: the firing is recorded, and the event says why it went nowhere.
        assertThat(events.get(0).get("delivered").asBoolean()).isFalse();
        assertThat(events.get(0).get("deliveryError").asText()).contains("NTFY_URL");
    }

    @Test
    @DisplayName("a percent-move alert re-arms on a new trading day")
    void percentMoveReArmsDaily() {
        api.postJson("/api/v1/market/alerts", Map.of("symbol", "MSFT", "rule", "pct_move", "threshold", "3"));

        vendorAnswers(quote("MSFT", "412.00", "400.00", "2026-09-25T15:00:00Z")); // +3.0%
        assertThat(refresh().json().get("alertsFired").asInt()).isEqualTo(1);
        vendorAnswers(quote("MSFT", "413.00", "400.00", "2026-09-25T15:05:00Z")); // still that day
        assertThat(refresh().json().get("alertsFired").asInt()).isZero();
        vendorAnswers(quote("MSFT", "400.00", "412.50", "2026-09-26T15:00:00Z")); // -3.03% next day
        assertThat(refresh().json().get("alertsFired").asInt()).isEqualTo(1);

        var events = api.get("/api/v1/market/alerts/events").json();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("message").asText()).contains("moved more than 3% today");
    }

    @Test
    @DisplayName("a paused alert does not fire, and resuming it re-arms it")
    void pausedAlertIsQuiet() {
        long id = api.postJson("/api/v1/market/alerts", Map.of("symbol", "AAPL", "rule", "below",
            "threshold", "100")).json().get("id").asLong();
        api.putJson("/api/v1/market/alerts/" + id + "/active", Map.of("active", false));
        vendorAnswers(quote("AAPL", "90.00", "95.00", "2026-09-25T15:00:00Z"));
        assertThat(refresh().json().get("alertsFired").asInt()).isZero();

        var resumed = api.putJson("/api/v1/market/alerts/" + id + "/active", Map.of("active", true));
        assertThat(resumed.json().get("armed").asBoolean()).isTrue();
        vendorAnswers(quote("AAPL", "89.00", "95.00", "2026-09-25T15:05:00Z"));
        assertThat(refresh().json().get("alertsFired").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("market data being off is a 503 with the sentence, not a fault")
    void marketDataOffIsAState() {
        api.postJson("/api/v1/market/watchlist", Map.of("symbol", "AAPL"));
        when(marketData.quotes(any())).thenThrow(
            new MarketDataClient.MarketDataOff("MARKET_DATA_PROVIDER is 'none'. Set it to 'alpaca' …"));
        when(marketData.status()).thenReturn(new MarketStatus("none", false, "MARKET_DATA_PROVIDER is 'none'."));

        var response = refresh();

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.body()).contains("MARKET_DATA_PROVIDER");
        var status = api.get("/api/v1/market/status").json();
        assertThat(status.get("available").asBoolean()).isFalse();
        assertThat(status.get("provider").asText()).isEqualTo("none");
        assertThat(status.get("notifierConfigured").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("what is not a ticker is refused before it reaches a vendor URL")
    void nonTickersAreRefused() {
        for (String bad : List.of("../etc", "AAPL MSFT", "", "toolongtobeaticker123")) {
            var response = api.postJson("/api/v1/market/watchlist", Map.of("symbol", bad));
            assertThat(response.status()).as(bad).isIn(400, 422);
        }
        var alert = api.postJson("/api/v1/market/alerts", Map.of("symbol", "AAPL", "rule", "sideways",
            "threshold", "1"));
        assertThat(alert.status()).isEqualTo(422);
        assertThat(alert.body()).contains("above, below or pct_move");
    }

    @Test
    @DisplayName("a refresh with nothing watched or held does not call the vendor")
    void nothingToRefresh() {
        var response = refresh();

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("fetched").asInt()).isZero();
        org.mockito.Mockito.verify(marketData, org.mockito.Mockito.never()).quotes(any());
    }
}
