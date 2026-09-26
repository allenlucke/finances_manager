package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import llc.feelingfroggy.finances.config.MarketProperties;
import llc.feelingfroggy.finances.service.Notifier;
import llc.feelingfroggy.finances.service.NtfyNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Alert delivery through ntfy")
class NtfyNotifierTest {

    private record Received(String title, String body) {}

    private static HttpServer serverAnswering(int status, AtomicReference<Received> seen) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.set(new Received(exchange.getRequestHeaders().getFirst("Title"), body));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    @DisplayName("posts the title and the message to the topic")
    void postsToTheTopic() throws IOException {
        var seen = new AtomicReference<Received>();
        var server = serverAnswering(200, seen);
        try {
            var notifier = new NtfyNotifier(new MarketProperties(false, Duration.ofMinutes(5),
                "http://localhost:" + server.getAddress().getPort() + "/finances-alerts"));

            assertThat(notifier.configured()).isTrue();
            notifier.send("Price alert", "AAPL is above 190: 195");

            assertThat(seen.get().title()).isEqualTo("Price alert");
            assertThat(seen.get().body()).isEqualTo("AAPL is above 190: 195");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("a refusal is a sentence, so the event records why it went nowhere")
    void refusalIsASentence() throws IOException {
        var server = serverAnswering(500, new AtomicReference<>());
        try {
            var notifier = new NtfyNotifier(new MarketProperties(false, Duration.ofMinutes(5),
                "http://localhost:" + server.getAddress().getPort() + "/t"));

            assertThatThrownBy(() -> notifier.send("x", "y"))
                .isInstanceOf(Notifier.NotificationFailed.class)
                .hasMessageContaining("500");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("a blank URL is a notifier that says it is not configured")
    void blankUrlIsOff() {
        var notifier = new NtfyNotifier(new MarketProperties(false, Duration.ofMinutes(5), ""));

        assertThat(notifier.configured()).isFalse();
        assertThatThrownBy(() -> notifier.send("x", "y"))
            .isInstanceOf(Notifier.NotificationFailed.class)
            .hasMessageContaining("NTFY_URL");
    }
}
