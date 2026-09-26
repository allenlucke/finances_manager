package llc.feelingfroggy.finances.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.domain.AlertEvent;
import llc.feelingfroggy.finances.domain.DomainRuleViolation;
import llc.feelingfroggy.finances.domain.PriceAlert;
import llc.feelingfroggy.finances.domain.Quote;
import llc.feelingfroggy.finances.domain.Security;
import llc.feelingfroggy.finances.domain.WatchlistEntry;
import llc.feelingfroggy.finances.repo.AlertEventRepository;
import llc.feelingfroggy.finances.repo.PriceAlertRepository;
import llc.feelingfroggy.finances.repo.QuoteRepository;
import llc.feelingfroggy.finances.repo.SecurityRepository;
import llc.feelingfroggy.finances.repo.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Watching the market (M7a): the watchlist, the quote refresh, and the alerts it evaluates.
 *
 * <p>A refresh asks the Python service for every symbol on the watchlist plus every non-cash
 * position in the latest holdings snapshots, stores each quote once per vendor timestamp, and then
 * feeds the newest quote for each active alert through its edge detector. An alert that fires
 * becomes an {@link AlertEvent}, which is pushed to the notifier and records whether that worked.
 *
 * <p>Nothing here touches the ledger. A price is not money.
 */
@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);
    private static final Pattern SYMBOL = Pattern.compile("^[A-Z][A-Z0-9.\\-]{0,15}$");

    private final MarketDataClient marketData;
    private final SecurityRepository securities;
    private final WatchlistRepository watchlist;
    private final QuoteRepository quotes;
    private final PriceAlertRepository alerts;
    private final AlertEventRepository events;
    private final Notifier notifier;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    private volatile Instant lastRefreshAt;
    private volatile String lastRefreshOutcome;

    public MarketService(MarketDataClient marketData, SecurityRepository securities,
                         WatchlistRepository watchlist, QuoteRepository quotes,
                         PriceAlertRepository alerts, AlertEventRepository events,
                         Notifier notifier, JdbcTemplate jdbc, Clock clock) {
        this.marketData = marketData;
        this.securities = securities;
        this.watchlist = watchlist;
        this.quotes = quotes;
        this.alerts = alerts;
        this.events = events;
        this.notifier = notifier;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Uppercased and checked: a ticker, not free text that reaches a vendor URL. */
    public static String normalizeSymbol(String raw) {
        String symbol = raw == null ? "" : raw.strip().toUpperCase();
        if (!SYMBOL.matcher(symbol).matches()) {
            throw new DomainRuleViolation("'" + raw + "' is not a ticker symbol");
        }
        return symbol;
    }

    /** The instrument for a symbol, created the first time it is watched or alerted on. */
    @Transactional
    public Security securityFor(Long userId, String rawSymbol) {
        String symbol = normalizeSymbol(rawSymbol);
        return securities.findByUserIdAndSymbol(userId, symbol)
            .orElseGet(() -> securities.save(new Security(userId, symbol, null, "unknown", false)));
    }

    @Transactional
    public WatchlistEntry watch(Long userId, String rawSymbol, String note) {
        Security security = securityFor(userId, rawSymbol);
        return watchlist.findByUserIdAndSecurityId(userId, security.getId())
            .map(existing -> {
                if (note != null && !note.isBlank()) {
                    existing.setNote(note);
                }
                return existing;
            })
            .orElseGet(() -> watchlist.save(new WatchlistEntry(userId, security, note)));
    }

    @Transactional
    public PriceAlert addAlert(Long userId, String rawSymbol, String rule, java.math.BigDecimal threshold,
                               String note) {
        Security security = securityFor(userId, rawSymbol);
        return alerts.save(new PriceAlert(userId, security, rule, threshold, note));
    }

    /**
     * Every symbol worth a quote: watched, held as something other than cash, or carrying an
     * active alert. The last one is what an alert means — an alert on a symbol nobody asked a
     * price for would never fire, and the first version did exactly that.
     */
    public List<String> symbolsToRefresh(Long userId) {
        var symbols = new LinkedHashSet<>(jdbc.queryForList("""
            SELECT s.symbol FROM watchlist w JOIN security s ON s.id = w.security_id
            WHERE w.user_id = ?
            UNION
            SELECT DISTINCT symbol FROM v_holding_live_value WHERE user_id = ? AND NOT is_cash
            UNION
            SELECT s.symbol FROM price_alert a JOIN security s ON s.id = a.security_id
            WHERE a.user_id = ? AND a.active
            ORDER BY 1
            """, String.class, userId, userId, userId));
        return new ArrayList<>(symbols);
    }

    /**
     * One refresh: fetch, store, evaluate. Returns what happened, for the screen and the log.
     *
     * @throws MarketDataClient.MarketDataOff when no provider is configured — a state, not a fault
     */
    @Transactional
    public RefreshResult refresh(Long userId) {
        List<String> symbols = symbolsToRefresh(userId);
        if (symbols.isEmpty()) {
            return new RefreshResult("none", 0, 0, List.of("Nothing to refresh: watch a symbol first."), 0);
        }
        var response = marketData.quotes(symbols);
        int stored = 0;
        var warnings = new ArrayList<>(response.safeWarnings());
        for (var wire : response.quotes()) {
            Optional<Security> security = securities.findByUserIdAndSymbol(userId, wire.symbol());
            if (security.isEmpty()) {
                continue;
            }
            if (wire.price() == null || wire.price().signum() <= 0 || wire.asOf() == null) {
                warnings.add(wire.symbol() + ": the provider sent no usable price");
                continue;
            }
            if (quotes.existsBySecurityIdAndAsOf(security.get().getId(), wire.asOf())) {
                continue;
            }
            quotes.save(new Quote(userId, security.get(), wire.asOf(), wire.price(),
                wire.previousClose(), wire.source()));
            stored++;
        }
        int fired = evaluateAlerts(userId);
        lastRefreshAt = Instant.now(clock);
        lastRefreshOutcome = response.quotes().size() + " quotes from " + response.provider()
            + (fired > 0 ? ", " + fired + " alert(s) fired" : "");
        // Counts only: a watchlist says what someone is thinking of buying.
        log.info("market refresh provider={} asked={} answered={} stored={} fired={}",
            response.provider(), symbols.size(), response.quotes().size(), stored, fired);
        return new RefreshResult(response.provider(), response.quotes().size(), stored, warnings, fired);
    }

    /** Feeds every active alert its security's newest quote. Returns how many fired. */
    @Transactional
    public int evaluateAlerts(Long userId) {
        Instant now = Instant.now(clock);
        int fired = 0;
        for (PriceAlert alert : alerts.findActiveForUser(userId)) {
            var latest = quotes.findTopBySecurityIdOrderByAsOfDescIdDesc(alert.getSecurity().getId());
            if (latest.isEmpty()) {
                continue;
            }
            boolean fire = alert.observe(latest.get(), now);
            alerts.save(alert);
            if (!fire) {
                continue;
            }
            fired++;
            var event = new AlertEvent(userId, alert, latest.get(), now, alert.describe(latest.get()));
            try {
                notifier.send("Price alert", event.getMessage());
                event.delivered();
            } catch (Notifier.NotificationFailed e) {
                event.notDelivered(e.getMessage());
            }
            events.save(event);
        }
        return fired;
    }

    public Instant lastRefreshAt() {
        return lastRefreshAt;
    }

    public String lastRefreshOutcome() {
        return lastRefreshOutcome;
    }

    public record RefreshResult(String provider, int fetched, int stored, List<String> warnings,
                                int alertsFired) {
    }
}
