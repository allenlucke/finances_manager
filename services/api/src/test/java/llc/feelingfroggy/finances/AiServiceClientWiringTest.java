package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.config.AiServiceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Guards the two things {@link AiServiceClientTest} cannot see, because it hand-builds the client
 * instead of letting Spring wire it.
 *
 * <p>Both of these were real M0 defects. The scaffold shipped without
 * {@code spring-boot-starter-restclient}, so no {@code RestClient.Builder} bean existed and the
 * application refused to start — while {@code mvn test} stayed green, because nothing in the suite
 * ever built a context. And {@code finances.ai.timeout} was never applied to the request factory,
 * so it was configuration that did nothing.
 */
class AiServiceClientWiringTest {

    /**
     * Spring Boot 4 splits autoconfiguration per technology: {@code spring-boot-starter-webmvc}
     * does NOT bring {@code RestClient.Builder} with it. If this fails, the application will fail
     * to start for real.
     */
    @Test
    void restClientBuilderIsAutoConfiguredSoTheClientCanBeWired() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                HttpClientAutoConfiguration.class,
                ImperativeHttpClientAutoConfiguration.class,
                RestClientAutoConfiguration.class))
            .withBean(AiServiceProperties.class, () -> new AiServiceProperties(null, null))
            .withBean(AiServiceClient.class)
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasSingleBean(AiServiceClient.class));
    }

    /**
     * A server that accepts the connection and then never answers. Without the configured read
     * timeout reaching the request factory, this call would hang instead of giving up.
     */
    @Test
    @Timeout(15) // a regression here hangs rather than fails; bound it so CI reports instead of stalling
    void appliesTheConfiguredTimeoutToASilentServer() throws Exception {
        try (var server = new SilentServer()) {
            var properties = new AiServiceProperties(
                "http://localhost:" + server.port(), Duration.ofMillis(300));
            var client = new AiServiceClient(properties, org.springframework.web.client.RestClient.builder());

            long startedAt = System.nanoTime();
            String status = client.health();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(status).isEqualTo("unreachable");
            assertThat(elapsed)
                .as("read timeout must be enforced, not left unbounded")
                .isLessThan(Duration.ofSeconds(5));
        }
    }

    /** Accepts connections and deliberately never writes a response. */
    private static final class SilentServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final List<Socket> accepted = new CopyOnWriteArrayList<>();
        private final Thread acceptor;

        SilentServer() throws IOException {
            this.serverSocket = new ServerSocket();
            this.serverSocket.bind(new InetSocketAddress("localhost", 0));
            this.acceptor = Thread.ofVirtual().start(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        accepted.add(serverSocket.accept());
                    } catch (IOException e) {
                        return;
                    }
                }
            });
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            for (Socket socket : accepted) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
            acceptor.join(Duration.ofSeconds(2));
        }
    }
}
