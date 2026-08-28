package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Guards the multipart upload against a bug that cost real time and produced no useful error.
 *
 * <p>The JDK's {@code HttpClient} defaults to HTTP/2 and, over cleartext, opens with an h2c upgrade
 * attempt. Uvicorn speaks HTTP/1.1 only — it logged "Unsupported upgrade request", the request
 * framing was mangled, and the multipart body arrived with <em>no parts at all</em>. FastAPI then
 * reported the file field as simply missing, which points at the client's form building rather
 * than at the protocol.
 *
 * <p>It was invisible until statement upload existed: the health check has no body to mangle, so
 * every earlier call worked.
 *
 * <p>This runs against the JDK's own HTTP/1.1-only server and asserts what actually reached it.
 */
@DisplayName("AI service multipart upload")
class AiServiceMultipartTest {

    private HttpServer server;
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/parse/csv", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            try (InputStream body = exchange.getRequestBody()) {
                received.set(new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] response = """
                {"source_format":"chase_card","transactions":[],"warnings":[]}"""
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private AiServiceClient clientForTestServer() {
        var properties = new AiServiceProperties(
            "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(5));
        return new AiServiceClient(properties, RestClient.builder());
    }

    @Test
    @DisplayName("the upload reaches an HTTP/1.1 server as a well-formed multipart body")
    void multipartArrivesIntact() {
        var result = clientForTestServer()
            .parseCsv("Date,Description,Amount\n2026-08-14,KROGER,-84.31\n"
                .getBytes(StandardCharsets.UTF_8), "chase.csv", "1");

        assertThat(result).isNotNull();
        assertThat(result.sourceFormat()).isEqualTo("chase_card");

        // A boundary must be present. Setting Content-Type by hand pins "multipart/form-data"
        // *without* one, and the receiver then finds no parts — the other half of this bug.
        assertThat(contentType.get()).startsWith("multipart/form-data");
        assertThat(contentType.get()).contains("boundary=");

        String body = received.get();
        assertThat(body).isNotBlank();
        // The part must be named "file": that is what the FastAPI signature binds.
        assertThat(body).contains("name=\"file\"");
        // A filename is required, or the service sees a plain field rather than an upload.
        assertThat(body).contains("filename=");
        // And the actual bytes have to be in there.
        assertThat(body).contains("KROGER");
    }

    @Test
    @DisplayName("a missing filename still sends an upload rather than a bare field")
    void filenameDefaultsWhenUnknown() {
        clientForTestServer().parseCsv("Date,Description,Amount\n".getBytes(StandardCharsets.UTF_8),
            null, "1");

        // Browsers can submit without one; multipart still requires a filename for a file part.
        assertThat(received.get()).contains("filename=");
    }
}
