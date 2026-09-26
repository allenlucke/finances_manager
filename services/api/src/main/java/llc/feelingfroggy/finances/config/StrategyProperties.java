package llc.feelingfroggy.finances.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Live strategies (M7c).
 *
 * @param evaluateEvery how often active strategies are asked for their opinion, when the market
 *     scheduler is on at all. One minute: an intraday strategy on minute bars needs it, and a
 *     daily one is unbothered by being asked often.
 */
@ConfigurationProperties(prefix = "finances.strategies")
public record StrategyProperties(Duration evaluateEvery) {

    public StrategyProperties {
        if (evaluateEvery == null || evaluateEvery.isZero() || evaluateEvery.isNegative()) {
            evaluateEvery = Duration.ofMinutes(1);
        }
    }
}
