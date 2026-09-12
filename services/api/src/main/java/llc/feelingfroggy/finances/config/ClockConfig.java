package llc.feelingfroggy.finances.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock the API reads "today" from.
 *
 * <p>{@code LocalDate.now()} with no argument uses the JVM's zone, which in a container is UTC. At
 * seven in the evening on the 31st in Kansas, UTC is already the 1st of next month, so every date
 * range the API defaulted itself — the MCP tools' "this month", a target with no start date — was
 * off by a month for those hours. The browser sends its own dates and was never affected, which
 * is why nothing noticed. {@code finances.zone} (env {@code APP_TIMEZONE}) is where the person is.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${finances.zone:America/Chicago}") String zone) {
        return clockFor(zone);
    }

    /** A wrong zone name fails at startup, loudly, rather than falling back to UTC in silence. */
    static Clock clockFor(String zone) {
        try {
            return Clock.system(ZoneId.of(zone.strip()));
        } catch (DateTimeException e) {
            throw new IllegalStateException(
                "finances.zone / APP_TIMEZONE is not a zone id: '" + zone + "'", e);
        }
    }
}
