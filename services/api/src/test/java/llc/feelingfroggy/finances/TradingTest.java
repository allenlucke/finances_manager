package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.ai.BrokerClient;
import llc.feelingfroggy.finances.ai.BrokerClient.WireBrokerStatus;
import llc.feelingfroggy.finances.ai.BrokerClient.WireOrder;
import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.ai.MarketDataClient.QuotesResponse;
import llc.feelingfroggy.finances.ai.MarketDataClient.WireQuote;
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
 * Orders (M7b, D-18) with trading ON and a $1,000 cap, so every gate after the confirmation is
 * exercised. {@link TradingOffTest} covers the default, which is off.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"finances.trading.enabled=true", "finances.trading.daily-notional-cap=1000.00"})
@DisplayName("Orders, with trading on")
class TradingTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private BrokerClient broker;
    @LocalServerPort private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        when(broker.status()).thenReturn(new WireBrokerStatus("fake", true, true, true,
            new BigDecimal("100000"), new BigDecimal("100000"), null));
        // A quote for AAPL at 190, so a market order can be sized.
        api.postJson("/api/v1/market/watchlist", Map.of("symbol", "AAPL"));
        when(marketData.quotes(any())).thenReturn(new QuotesResponse("fake", List.of(
            new WireQuote("AAPL", new BigDecimal("190.00"), new BigDecimal("188.00"),
                Instant.parse("2026-09-26T15:00:00Z"), "fake")), List.of()));
        api.postJson("/api/v1/market/quotes/refresh", Map.of());
    }

    private long propose(String quantity) {
        var response = api.postJson("/api/v1/orders", Map.of("symbol", "AAPL", "venue", "paper",
            "side", "buy", "quantity", quantity, "orderType", "market", "proposedBy", "assistant",
            "rationale", "test"));
        assertThat(response.status()).isEqualTo(201);
        return response.json().get("id").asLong();
    }

    private ApiClient.Response confirm(long id, String quantity) {
        return api.postJson("/api/v1/orders/" + id + "/confirm",
            Map.of("symbol", "AAPL", "side", "buy", "quantity", quantity, "actor", "person"));
    }

    @Test
    @DisplayName("a confirmed order is sent with our own id, and learns its fill on sync")
    void confirmSubmitFill() {
        when(broker.submit(any())).thenReturn(new WireOrder("fake", "b-1", "accepted", "new",
            Instant.parse("2026-09-26T15:01:00Z"), null, BigDecimal.ZERO, null));
        long id = propose("2");

        var confirmed = confirm(id, "2");

        assertThat(confirmed.status()).isEqualTo(200);
        assertThat(confirmed.json().get("status").asText()).isEqualTo("accepted");
        assertThat(confirmed.json().get("brokerOrderId").asText()).isEqualTo("b-1");
        assertThat(new BigDecimal(confirmed.json().get("notionalEstimate").asText()))
            .isEqualByComparingTo("380.00");
        var sent = ArgumentCaptor.forClass(BrokerClient.OrderRequest.class);
        verify(broker).submit(sent.capture());
        assertThat(sent.getValue().clientOrderId()).startsWith("fm-");
        assertThat(sent.getValue().quantity()).isEqualTo("2");
        assertThat(sent.getValue().symbol()).isEqualTo("AAPL");

        when(broker.lookup("b-1")).thenReturn(new WireOrder("fake", "b-1", "filled", "filled",
            Instant.parse("2026-09-26T15:01:00Z"), Instant.parse("2026-09-26T15:02:00Z"),
            new BigDecimal("2"), new BigDecimal("189.95")));
        var synced = api.postJson("/api/v1/orders/sync", Map.of());
        assertThat(synced.json().get("changed").asInt()).isEqualTo(1);

        var order = api.get("/api/v1/orders").json().get(0);
        assertThat(order.get("status").asText()).isEqualTo("filled");
        assertThat(new BigDecimal(order.get("filledAvgPrice").asText())).isEqualByComparingTo("189.95");

        // The audit trail: who did what.
        var events = api.get("/api/v1/orders/" + id + "/events").json();
        assertThat(events).extracting(e -> e.get("toStatus").asText())
            .containsExactly("draft", "confirmed", "accepted", "filled");
        assertThat(events).extracting(e -> e.get("actor").asText())
            .containsExactly("assistant", "person", "broker", "broker");
        // And nothing entered the ledger.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class)).isZero();
    }

    @Test
    @DisplayName("a confirmation that does not restate the draft confirms nothing")
    void mismatchedEchoIsRefused() {
        long id = propose("2");

        var wrongQuantity = confirm(id, "3");
        var wrongSide = api.postJson("/api/v1/orders/" + id + "/confirm",
            Map.of("symbol", "AAPL", "side", "sell", "quantity", "2"));

        assertThat(wrongQuantity.status()).isEqualTo(422);
        assertThat(wrongQuantity.body()).contains("does not match the draft");
        assertThat(wrongQuantity.body()).contains("buy 2 AAPL at market");
        assertThat(wrongSide.status()).isEqualTo(422);
        verify(broker, never()).submit(any());
        assertThat(api.get("/api/v1/orders").json().get(0).get("status").asText()).isEqualTo("draft");
    }

    @Test
    @DisplayName("the daily cap refuses the order that would cross it, with the numbers")
    void dailyCapIsEnforced() {
        when(broker.submit(any())).thenReturn(new WireOrder("fake", "b-1", "accepted", "new",
            null, null, BigDecimal.ZERO, null));
        long first = propose("4");      // 760
        var firstConfirm = confirm(first, "4");
        assertThat(firstConfirm.status()).as(firstConfirm.body()).isEqualTo(200);
        long second = propose("2");     // 380 → 1140 > 1000

        var refused = confirm(second, "2");

        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.body()).contains("Over the daily cap");
        assertThat(refused.body()).contains("760");
        assertThat(refused.body()).contains("1000");
        verify(broker, org.mockito.Mockito.times(1)).submit(any());
        // The refused order is cancelled with the reason on record, and the first is untouched.
        var all = api.get("/api/v1/orders").json();
        assertThat(all.get(0).get("status").asText()).isEqualTo("cancelled");
        assertThat(all.get(1).get("status").asText()).isEqualTo("accepted");
        assertThat(api.get("/api/v1/orders/" + second + "/events").json())
            .extracting(e -> e.get("actor").asText()).containsExactly("assistant", "person", "system");
        var status = api.get("/api/v1/orders/status").json();
        assertThat(new BigDecimal(status.get("usedToday").asText())).isEqualByComparingTo("760.00");
        assertThat(new BigDecimal(status.get("remainingToday").asText())).isEqualByComparingTo("240.00");
        assertThat(status.get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a broker refusal marks the order failed with the broker's sentence")
    void brokerRefusalIsRecorded() {
        when(broker.submit(any())).thenThrow(
            new llc.feelingfroggy.finances.ai.AiServiceClient.AiServiceException("Alpaca rejected the order: insufficient buying power"));
        long id = propose("1");

        var refused = confirm(id, "1");

        assertThat(refused.status()).isEqualTo(422);
        assertThat(refused.body()).contains("insufficient buying power");
        var order = api.get("/api/v1/orders").json().get(0);
        assertThat(order.get("status").asText()).isEqualTo("failed");
        assertThat(order.get("lastError").asText()).contains("insufficient buying power");
    }

    @Test
    @DisplayName("an open order is cancelled through the broker; a draft is cancelled here")
    void cancelling() {
        when(broker.submit(any())).thenReturn(new WireOrder("fake", "b-9", "accepted", "new",
            null, null, BigDecimal.ZERO, null));
        when(broker.cancel("b-9")).thenReturn(new WireOrder("fake", "b-9", "cancelled", "canceled",
            null, null, BigDecimal.ZERO, null));
        long open = propose("1");
        confirm(open, "1");
        long draft = propose("1");

        assertThat(api.postJson("/api/v1/orders/" + open + "/cancel", Map.of()).json()
            .get("status").asText()).isEqualTo("cancelled");
        verify(broker).cancel("b-9");
        assertThat(api.postJson("/api/v1/orders/" + draft + "/cancel", Map.of()).json()
            .get("status").asText()).isEqualTo("cancelled");
        verify(broker, org.mockito.Mockito.times(1)).cancel(anyString());
    }

    @Test
    @DisplayName("a manual ticket stops at confirmed, goes to Fidelity by hand, and is marked placed")
    void manualTicket() {
        var proposed = api.postJson("/api/v1/orders", Map.of("symbol", "AAPL", "venue", "manual",
            "side", "sell", "quantity", "3", "orderType", "limit", "limitPrice", "195.00"));
        long id = proposed.json().get("id").asLong();
        assertThat(new BigDecimal(proposed.json().get("notionalEstimate").asText()))
            .isEqualByComparingTo("585.00");

        var confirmed = api.postJson("/api/v1/orders/" + id + "/confirm",
            Map.of("symbol", "aapl", "side", "sell", "quantity", "3", "limitPrice", "195"));
        assertThat(confirmed.json().get("status").asText()).isEqualTo("confirmed");
        verify(broker, never()).submit(any());

        var placed = api.postJson("/api/v1/orders/" + id + "/placed", Map.of("fillPrice", "195.10"));
        assertThat(placed.json().get("status").asText()).isEqualTo("placed_manually");
        assertThat(new BigDecimal(placed.json().get("filledAvgPrice").asText())).isEqualByComparingTo("195.10");
        // A manual ticket does not count against the paper cap.
        assertThat(new BigDecimal(api.get("/api/v1/orders/status").json().get("usedToday").asText()))
            .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a market order with no quote cannot be sized, and says what to do")
    void unsizedOrderIsRefused() {
        var response = api.postJson("/api/v1/orders", Map.of("symbol", "ZZZQ", "venue", "paper",
            "side", "buy", "quantity", "1", "orderType", "market"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body()).contains("Refresh quotes first, or give a limit price");
    }
}
