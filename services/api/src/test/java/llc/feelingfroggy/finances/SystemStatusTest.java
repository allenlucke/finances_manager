package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import llc.feelingfroggy.finances.ai.BrokerClient;
import llc.feelingfroggy.finances.ai.BrokerClient.WireBrokerStatus;
import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.ai.MarketDataClient.MarketStatus;
import llc.feelingfroggy.finances.service.Notifier;
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

/** The machinery's own account of itself: honest about what is off, unreachable or never run. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("System status")
class SystemStatusTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private MarketDataClient marketData;
    @MockitoBean private BrokerClient broker;
    @MockitoBean private Notifier notifier;
    @LocalServerPort private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid", "displayName", "Owner",
            "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        when(marketData.status()).thenReturn(new MarketStatus("none", false, "MARKET_DATA_PROVIDER is 'none'."));
        when(broker.status()).thenReturn(new WireBrokerStatus("none", false, true, null, null, null, "TRADING_BROKER is 'none'."));
        when(notifier.configured()).thenReturn(false);
    }

    @Test
    @DisplayName("says what is off and what has never run, then what did run")
    void status() {
        var before = api.get("/api/v1/system/status").json();

        assertThat(before.get("aiService").asText()).isEqualTo("unreachable");
        assertThat(before.get("marketData").get("available").asBoolean()).isFalse();
        assertThat(before.get("marketScheduled").asBoolean()).isFalse();
        assertThat(before.get("tradingEnabled").asBoolean()).isFalse();
        assertThat(new BigDecimal(before.get("tradingDailyCap").asText())).isEqualByComparingTo("1000.00");
        assertThat(before.get("digestScheduled").asBoolean()).isFalse();
        assertThat(before.get("lastDigest").isNull()).isTrue();
        assertThat(before.get("lastSnapshot").isNull()).isTrue();
        assertThat(before.get("activeStrategies").asInt()).isZero();
        assertThat(before.get("notifierConfigured").asBoolean()).isFalse();
        assertThat(before.get("zone").asText()).isEqualTo("America/Chicago");

        // A snapshot needs a ledger to snapshot: one account, one row.
        long entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        long checking = api.postJson("/api/v1/accounts", Map.of("ledgerEntityId", entityId, "name", "Checking",
            "accountType", "checking")).json().get("id").asLong();
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate",
            LocalDate.now(ZoneId.of("America/Chicago")).toString(), "amount", "10.00", "direction", "credit",
            "description", "Opening"));
        api.postJson("/api/v1/digest/send", Map.of());
        api.postJson("/api/v1/reports/net-worth/snapshot", Map.of());
        var after = api.get("/api/v1/system/status").json();

        assertThat(after.get("lastDigest").get("sent").asBoolean()).isFalse();
        assertThat(after.get("lastDigest").get("deliveryError").asText()).contains("NTFY_URL");
        assertThat(after.get("lastSnapshot").asText()).isEqualTo(LocalDate.now(ZoneId.of("America/Chicago")).toString());
    }
}
