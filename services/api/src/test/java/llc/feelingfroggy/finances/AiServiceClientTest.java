package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * No database, no Spring context — this runs anywhere, including CI without Postgres.
 *
 * <p>The refusal cases drive the client against the JDK's own HTTP server answering as FastAPI
 * does, so what is asserted is what production receives — not a mocked exception carrying the
 * sentence the test wants to see, which is how the parser's reasons went unshown for two weeks.
 */
class AiServiceClientTest {

    private static final byte[] A_FILE = "Date,Description,Amount\n".getBytes(StandardCharsets.UTF_8);

    /** A parser that answers every upload with one fixed status and body. */
    private static AiServiceClient parserAnswering(int status, String body) throws IOException {
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
        var properties = new AiServiceProperties(
            "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(5));
        return new AiServiceClient(properties, RestClient.builder());
    }

    @Test
    @DisplayName("a parser's 422 verdict is the exception message, word for word")
    void aRefusalCarriesTheParsersOwnSentence() throws IOException {
        var client = parserAnswering(422,
            "{\"detail\":\"No parser matches this file. Known formats: chase_card, cacu\"}");

        assertThatThrownBy(() -> client.parseCsv(A_FILE, "x.csv", "1"))
            .isInstanceOf(AiServiceClient.AiServiceException.class)
            .hasMessage("No parser matches this file. Known formats: chase_card, cacu");
    }

    @Test
    @DisplayName("a positions refusal is passed through the same way")
    void aPositionsRefusalCarriesTheParsersOwnSentence() throws IOException {
        var client = parserAnswering(422,
            "{\"detail\":\"Not a positions export: missing Symbol. A transaction history export has different columns.\"}");

        assertThatThrownBy(() -> client.parsePositions(A_FILE, "x.csv"))
            .isInstanceOf(AiServiceClient.AiServiceException.class)
            .hasMessageStartingWith("Not a positions export: missing Symbol");
    }

    @Test
    @DisplayName("a server fault gets the fixed sentence, never the body")
    void aFaultGetsTheFixedSentence() throws IOException {
        var client = parserAnswering(500, "{\"detail\":\"Traceback (most recent call last) …\"}");

        assertThatThrownBy(() -> client.parseCsv(A_FILE, "x.csv", "1"))
            .isInstanceOf(AiServiceClient.AiServiceException.class)
            .hasMessage("The statement could not be parsed.");
    }

    @Test
    @DisplayName("FastAPI's validation list is not a sentence and is not echoed")
    void aValidationListGetsTheFixedSentence() throws IOException {
        var client = parserAnswering(422,
            "{\"detail\":[{\"loc\":[\"body\",\"file\"],\"msg\":\"field required\"}]}");

        assertThatThrownBy(() -> client.parseCsv(A_FILE, "x.csv", "1"))
            .isInstanceOf(AiServiceClient.AiServiceException.class)
            .hasMessage("The statement could not be parsed.");
    }

    @Test
    @DisplayName("a detail longer than a verdict is cut, not shown whole")
    void aLongDetailIsCut() {
        String verdict = AiServiceClient.detailOf("{\"detail\":\"" + "x".repeat(1000) + "\"}");

        assertThat(verdict).hasSize(AiServiceClient.MAX_DETAIL_LENGTH);
        assertThat(verdict).endsWith("…");
    }

    @Test
    void reportsUnreachableWhenAiServiceIsDown() {
        var properties = new AiServiceProperties("http://localhost:59999", Duration.ofMillis(250));
        var client = new AiServiceClient(properties, RestClient.builder());

        assertThat(client.health()).isEqualTo("unreachable");
    }

    @Test
    void appliesDefaultsForBlankConfiguration() {
        var properties = new AiServiceProperties("  ", null);

        assertThat(properties.baseUrl()).isEqualTo("http://localhost:8000");
        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
