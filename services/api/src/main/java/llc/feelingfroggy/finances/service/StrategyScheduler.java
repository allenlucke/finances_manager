package llc.feelingfroggy.finances.service;

import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Asks active strategies for their opinion on a timer (M7c). Lives behind the same switch as the
 * quote poll, and is a quiet no-op with no provider. A signal becomes a draft, never an order.
 */
@Configuration
@ConditionalOnProperty(name = "finances.market.scheduled", havingValue = "true")
public class StrategyScheduler {

    private static final Logger log = LoggerFactory.getLogger(StrategyScheduler.class);

    private final StrategyService strategies;
    private final AppUserRepository users;
    private boolean saidOff;

    public StrategyScheduler(StrategyService strategies, AppUserRepository users) {
        this.strategies = strategies;
        this.users = users;
    }

    @Scheduled(fixedDelayString = "${finances.strategies.evaluate-every:PT1M}", initialDelayString = "PT45S")
    public void tick() {
        for (var user : users.findAll()) {
            try {
                var outcomes = strategies.evaluateAll(user.getId());
                saidOff = false;
                outcomes.stream().filter(o -> o.orderId() != null && o.action() != null)
                    .forEach(o -> log.info("strategy {} proposed order #{}", o.strategyId(), o.orderId()));
            } catch (MarketDataClient.MarketDataOff off) {
                if (!saidOff) {
                    log.info("strategy evaluation skipped: {}", off.getMessage());
                    saidOff = true;
                }
            } catch (RuntimeException e) {
                log.warn("strategy evaluation failed: {}", e.getMessage());
            }
        }
    }
}
