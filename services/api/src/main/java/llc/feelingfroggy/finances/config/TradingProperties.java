package llc.feelingfroggy.finances.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The two bounds on order execution (M7b, D-18).
 *
 * @param enabled the kill switch. Off by default, everywhere: a confirmed paper order is refused
 *     with a sentence naming this until someone turns it on.
 * @param dailyNotionalCap the most a day's confirmed paper orders may add up to, in dollars,
 *     sized at the limit price or the quote the draft was made against. A mistake in the
 *     assistant's reasoning is bounded by this number.
 */
@ConfigurationProperties(prefix = "finances.trading")
public record TradingProperties(boolean enabled, BigDecimal dailyNotionalCap) {

    public TradingProperties {
        if (dailyNotionalCap == null || dailyNotionalCap.signum() < 0) {
            dailyNotionalCap = new BigDecimal("1000.00");
        }
    }
}
