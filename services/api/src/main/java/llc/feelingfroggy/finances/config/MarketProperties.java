package llc.feelingfroggy.finances.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Watching the market (M7a).
 *
 * @param scheduled whether the API polls for quotes on its own. Off in tests and off by default;
 *     compose turns it on, and a poll with no provider configured is a quiet no-op.
 * @param refreshEvery how often. Five minutes: the free feed is not tick data and nothing here
 *     trades on the poll.
 * @param ntfyUrl where a fired alert is posted — an ntfy topic URL. Blank means alerts are recorded
 *     and shown but not pushed anywhere, and each event says so.
 */
@ConfigurationProperties(prefix = "finances.market")
public record MarketProperties(boolean scheduled, Duration refreshEvery, String ntfyUrl) {

    public MarketProperties {
        if (refreshEvery == null || refreshEvery.isZero() || refreshEvery.isNegative()) {
            refreshEvery = Duration.ofMinutes(5);
        }
        if (ntfyUrl == null) {
            ntfyUrl = "";
        }
    }

    public boolean notifierConfigured() {
        return !ntfyUrl.isBlank();
    }
}
