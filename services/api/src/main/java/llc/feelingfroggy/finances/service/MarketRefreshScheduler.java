package llc.feelingfroggy.finances.service;

import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Polls for quotes on a timer (M7a).
 *
 * <p>Exists only when {@code finances.market.scheduled} is true — compose sets it, tests do not —
 * so no test ever races a background refresh. With no provider configured every tick is a quiet
 * no-op: {@link MarketDataClient.MarketDataOff} is logged once at debug, not every five minutes
 * at warn.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "finances.market.scheduled", havingValue = "true")
public class MarketRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketRefreshScheduler.class);

    private final MarketService market;
    private final AppUserRepository users;
    private boolean saidOff;

    public MarketRefreshScheduler(MarketService market, AppUserRepository users) {
        this.market = market;
        this.users = users;
    }

    @Scheduled(fixedDelayString = "${finances.market.refresh-every:PT5M}", initialDelayString = "PT30S")
    public void tick() {
        for (var user : users.findAll()) {
            try {
                market.refresh(user.getId());
                saidOff = false;
            } catch (MarketDataClient.MarketDataOff off) {
                if (!saidOff) {
                    log.info("market refresh skipped: {}", off.getMessage());
                    saidOff = true;
                }
            } catch (RuntimeException e) {
                log.warn("market refresh failed: {}", e.getMessage());
            }
        }
    }
}
