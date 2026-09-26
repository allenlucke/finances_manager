package llc.feelingfroggy.finances.api;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.BrokerClient;
import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.config.DigestProperties;
import llc.feelingfroggy.finances.config.MarketProperties;
import llc.feelingfroggy.finances.config.StrategyProperties;
import llc.feelingfroggy.finances.config.TradingProperties;
import llc.feelingfroggy.finances.repo.StrategyRepository;
import llc.feelingfroggy.finances.service.DigestService;
import llc.feelingfroggy.finances.service.MarketService;
import llc.feelingfroggy.finances.service.Notifier;
import llc.feelingfroggy.finances.service.SnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Whether the machinery behind the screens is alive: the AI service, the market-data provider and
 * when it last answered, the broker and the trading switch, the digest and when it last went out,
 * the last net-worth snapshot, the live strategies. The same facts `make doctor` prints from the
 * outside, from the inside, for the person looking at a homelab box from their phone.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final AiServiceClient ai;
    private final MarketDataClient marketData;
    private final MarketService market;
    private final MarketProperties marketProperties;
    private final BrokerClient broker;
    private final TradingProperties trading;
    private final DigestProperties digestProperties;
    private final DigestService digest;
    private final SnapshotService snapshots;
    private final StrategyRepository strategies;
    private final StrategyProperties strategyProperties;
    private final Notifier notifier;
    private final CurrentUser currentUser;
    private final Clock clock;

    public SystemController(AiServiceClient ai, MarketDataClient marketData, MarketService market,
                            MarketProperties marketProperties, BrokerClient broker, TradingProperties trading,
                            DigestProperties digestProperties, DigestService digest, SnapshotService snapshots,
                            StrategyRepository strategies, StrategyProperties strategyProperties,
                            Notifier notifier, CurrentUser currentUser, Clock clock) {
        this.ai = ai;
        this.marketData = marketData;
        this.market = market;
        this.marketProperties = marketProperties;
        this.broker = broker;
        this.trading = trading;
        this.digestProperties = digestProperties;
        this.digest = digest;
        this.snapshots = snapshots;
        this.strategies = strategies;
        this.strategyProperties = strategyProperties;
        this.notifier = notifier;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/status")
    public SystemStatus status() {
        Long userId = currentUser.id();
        var runs = digest.runs(userId, 1);
        return new SystemStatus(Instant.now(clock), clock.getZone().getId(), ai.health(), marketData.status(),
            marketProperties.scheduled(), marketProperties.refreshEvery().toString(), market.lastRefreshAt(),
            market.lastRefreshOutcome(), broker.status(), trading.enabled(), trading.dailyNotionalCap(),
            digestProperties.scheduled(), runs.isEmpty() ? null : runs.get(0), snapshots.latestAsOf(userId),
            strategies.findActiveForUser(userId).size(), strategyProperties.evaluateEvery().toString(),
            notifier.configured());
    }

    public record SystemStatus(Instant now, String zone, String aiService, MarketDataClient.MarketStatus marketData,
                               boolean marketScheduled, String marketRefreshEvery, Instant lastMarketRefresh,
                               String lastMarketRefreshOutcome, BrokerClient.WireBrokerStatus broker,
                               boolean tradingEnabled, BigDecimal tradingDailyCap, boolean digestScheduled,
                               DigestService.Run lastDigest, LocalDate lastSnapshot, int activeStrategies,
                               String strategyEvaluateEvery, boolean notifierConfigured) {
    }
}
