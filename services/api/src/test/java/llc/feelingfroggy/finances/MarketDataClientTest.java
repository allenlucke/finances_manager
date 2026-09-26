package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@DisplayName("The market-data client")
class MarketDataClientTest {

    private static MarketDataClient clientFor(int status, String body) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
            server.stop(0);
        });
        server.start();
        return new MarketDataClient(new AiServiceProperties(
            "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(5)), RestClient.builder());
    }

    @Test
    @DisplayName("quotes arrive as decimals with the vendor's own timestamp")
    void parsesQuotes() throws IOException {
        var client = clientFor(200, """
            {"provider":"alpaca","quotes":[{"symbol":"AAPL","price":"189.30","previous_close":"187.10",
             "as_of":"2026-09-25T19:59:58Z","source":"alpaca"}],"warnings":["NOPE: no quote from Alpaca"]}
            """);

        var response = client.quotes(List.of("AAPL", "NOPE"));

        assertThat(response.provider()).isEqualTo("alpaca");
        assertThat(response.quotes()).hasSize(1);
        assertThat(response.quotes().getFirst().price()).isEqualByComparingTo(new BigDecimal("189.30"));
        assertThat(response.quotes().getFirst().asOf()).isEqualTo(Instant.parse("2026-09-25T19:59:58Z"));
        assertThat(response.safeWarnings()).containsExactly("NOPE: no quote from Alpaca");
    }

    @Test
    @DisplayName("a 503 is 'market data is off', carrying the service's sentence")
    void offIsAState() throws IOException {
        var client = clientFor(503, "{\"detail\":\"MARKET_DATA_PROVIDER is 'none'. Set it to 'alpaca' …\"}");

        assertThatThrownBy(() -> client.quotes(List.of("AAPL")))
            .isInstanceOf(MarketDataClient.MarketDataOff.class)
            .hasMessageContaining("MARKET_DATA_PROVIDER");
    }

    @Test
    @DisplayName("a vendor refusal relayed as 502 is a fault with the service's sentence")
    void refusalIsAFault() throws IOException {
        var client = clientFor(502, "{\"detail\":\"Alpaca refused the API key\"}");

        assertThatThrownBy(() -> client.quotes(List.of("AAPL")))
            .isInstanceOf(llc.feelingfroggy.finances.ai.AiServiceClient.AiServiceException.class)
            .hasMessage("Alpaca refused the API key");
    }
}
