package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import llc.feelingfroggy.finances.ai.BrokerClient;
import llc.feelingfroggy.finances.ai.BrokerClient.WireBrokerStatus;
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

/** The default: trading is off, and a confirmed paper order goes nowhere but says why. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Orders, with trading off (the default)")
class TradingOffTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
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
        when(broker.status()).thenReturn(new WireBrokerStatus("none", false, true, null, null, null,
            "TRADING_BROKER is 'none'."));
    }

    @Test
    @DisplayName("the switch is off by default, the confirmation is kept, and nothing is sent")
    void offByDefault() {
        var status = api.get("/api/v1/orders/status").json();
        assertThat(status.get("enabled").asBoolean()).isFalse();
        assertThat(new BigDecimal(status.get("dailyNotionalCap").asText())).isEqualByComparingTo("1000.00");

        long id = api.postJson("/api/v1/orders", Map.of("symbol", "AAPL", "venue", "paper",
            "side", "buy", "quantity", "1", "orderType", "limit", "limitPrice", "180")).json().get("id").asLong();
        var confirmed = api.postJson("/api/v1/orders/" + id + "/confirm",
            Map.of("symbol", "AAPL", "side", "buy", "quantity", "1", "limitPrice", "180.00"));

        assertThat(confirmed.status()).isEqualTo(422);
        assertThat(confirmed.body()).contains("TRADING_ENABLED");
        verify(broker, never()).submit(any());
        assertThat(api.get("/api/v1/orders").json().get(0).get("status").asText()).isEqualTo("confirmed");
        // The trail says the person confirmed and why nothing went out.
        var events = api.get("/api/v1/orders/" + id + "/events").json();
        assertThat(events).extracting(e -> e.get("toStatus").asText()).containsExactly("draft", "confirmed");
        assertThat(events.get(1).get("note").asText()).contains("not sent");
        // Confirming again, with the same echo, is allowed — it is how the order goes once the switch is on.
        var again = api.postJson("/api/v1/orders/" + id + "/confirm",
            Map.of("symbol", "AAPL", "side", "buy", "quantity", "1", "limitPrice", "180.00"));
        assertThat(again.status()).isEqualTo(422);
        assertThat(again.body()).contains("TRADING_ENABLED");
    }
}
