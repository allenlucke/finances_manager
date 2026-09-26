package llc.feelingfroggy.finances.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import llc.feelingfroggy.finances.config.MarketProperties;
import org.springframework.stereotype.Component;

/**
 * Posts an alert to an ntfy topic (M7a, D-18).
 *
 * <p>ntfy is a plain HTTP push service with a phone app: POST a body to a topic URL and every
 * subscriber gets it. It can be self-hosted on the homelab box and reached over Tailscale, or the
 * public server used with a long random topic name. The URL is the only configuration, and a blank
 * one turns delivery off — the alert still fires and is recorded, and the event says it was not
 * delivered because nothing is configured.
 *
 * <p>The message carries the symbol and the price. That is what a person subscribed to; nothing
 * about balances or accounts is ever sent.
 */
@Component
public class NtfyNotifier implements Notifier {

    private final MarketProperties properties;
    private final HttpClient http;

    public NtfyNotifier(MarketProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Override
    public boolean configured() {
        return properties.notifierConfigured();
    }

    @Override
    public void send(String title, String message) {
        if (!configured()) {
            throw new NotificationFailed("No notification channel is configured (NTFY_URL is blank).");
        }
        var request = HttpRequest.newBuilder(URI.create(properties.ntfyUrl()))
            .timeout(Duration.ofSeconds(10))
            .header("Title", title)
            .header("Tags", "chart_with_upwards_trend")
            .header("Content-Type", "text/plain; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
            .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                throw new NotificationFailed("ntfy answered " + response.statusCode());
            }
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new NotificationFailed("ntfy could not be reached", e);
        }
    }
}
