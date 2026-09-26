package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import llc.feelingfroggy.finances.ai.MarketDataClient;
import llc.feelingfroggy.finances.config.MarketProperties;
import llc.feelingfroggy.finances.domain.AlertEvent;
import llc.feelingfroggy.finances.domain.PriceAlert;
import llc.feelingfroggy.finances.domain.WatchlistEntry;
import llc.feelingfroggy.finances.repo.AlertEventRepository;
import llc.feelingfroggy.finances.repo.PriceAlertRepository;
import llc.feelingfroggy.finances.repo.WatchlistRepository;
import llc.feelingfroggy.finances.service.MarketService;
import llc.feelingfroggy.finances.service.Notifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Watching the market (M7a): the watchlist, quotes, alerts and their history.
 *
 * <p>Everything here is a price or a rule about a price. None of it is money, none of it moves a
 * balance, and the holdings figure it offers is labelled "at last quote" beside the snapshot value
 * rather than replacing it.
 */
@RestController
@RequestMapping("/api/v1/market")
public class MarketController {

    private final MarketService market;
    private final MarketDataClient marketData;
    private final MarketProperties properties;
    private final Notifier notifier;
    private final WatchlistRepository watchlist;
    private final PriceAlertRepository alerts;
    private final AlertEventRepository events;
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public MarketController(MarketService market, MarketDataClient marketData,
                            MarketProperties properties, Notifier notifier,
                            WatchlistRepository watchlist, PriceAlertRepository alerts,
                            AlertEventRepository events, JdbcTemplate jdbc, CurrentUser currentUser) {
        this.market = market;
        this.marketData = marketData;
        this.properties = properties;
        this.notifier = notifier;
        this.watchlist = watchlist;
        this.alerts = alerts;
        this.events = events;
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    // ---------- status ----------

    /** Whether quotes can be had at all, and when they were last fetched. */
    @GetMapping("/status")
    public StatusView status() {
        var upstream = marketData.status();
        return new StatusView(upstream.provider(), upstream.available(), upstream.detail(),
            properties.scheduled(), properties.refreshEvery().toString(), notifier.configured(),
            market.lastRefreshAt(), market.lastRefreshOutcome());
    }

    // ---------- watchlist ----------

    @GetMapping("/watchlist")
    public List<WatchlistView> watchlist() {
        return watchlist.findAllForUser(currentUser.id()).stream().map(WatchlistView::of).toList();
    }

    @PostMapping("/watchlist")
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistView watch(@Valid @RequestBody Watch request) {
        return WatchlistView.of(market.watch(currentUser.id(), request.symbol(), request.note()));
    }

    @DeleteMapping("/watchlist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void unwatch(@PathVariable Long id) {
        var entry = watchlist.findByIdAndUserId(id, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        watchlist.delete(entry);
    }

    // ---------- quotes ----------

    /** The latest quote for every watched or held symbol, with today's move. */
    @GetMapping("/quotes")
    public List<QuoteView> quotes() {
        return jdbc.query("""
            SELECT s.id AS security_id, s.symbol, s.name,
                   q.price, q.previous_close, q.change_pct, q.as_of, q.source,
                   EXISTS (SELECT 1 FROM watchlist w WHERE w.security_id = s.id) AS watched,
                   EXISTS (SELECT 1 FROM v_holding_live_value h
                           WHERE h.security_id = s.id AND NOT h.is_cash)         AS held
            FROM security s
            LEFT JOIN v_latest_quote q ON q.security_id = s.id
            WHERE s.user_id = ?
              AND (EXISTS (SELECT 1 FROM watchlist w WHERE w.security_id = s.id)
                   OR EXISTS (SELECT 1 FROM v_holding_live_value h
                              WHERE h.security_id = s.id AND NOT h.is_cash))
            ORDER BY s.symbol
            """,
            (rs, row) -> new QuoteView(rs.getLong("security_id"), rs.getString("symbol"),
                rs.getString("name"), rs.getBigDecimal("price"), rs.getBigDecimal("previous_close"),
                rs.getBigDecimal("change_pct"), instant(rs.getObject("as_of", OffsetDateTime.class)),
                rs.getString("source"), rs.getBoolean("watched"), rs.getBoolean("held")),
            currentUser.id());
    }

    /**
     * Fetch now rather than waiting for the timer. With no provider configured this is a 503
     * carrying the service's own sentence — {@code ApiExceptionHandler} maps
     * {@link MarketDataClient.MarketDataOff} to it, because a 5xx reason is otherwise treated as a
     * fault's explanation and hidden.
     */
    @PostMapping("/quotes/refresh")
    public MarketService.RefreshResult refresh() {
        return market.refresh(currentUser.id());
    }

    /**
     * Every position in the latest snapshots valued two ways: as the file said, and at the latest
     * quote. The second never replaces the first anywhere else — a quote is a moment.
     */
    @GetMapping("/holdings")
    public List<HoldingAtMarketView> holdingsAtMarket() {
        return jdbc.query("""
            SELECT account_id, account_name, security_id, symbol, security_name, is_cash,
                   snapshot_as_of, quantity, snapshot_price, snapshot_value,
                   live_price, quote_as_of, quote_source, live_value
            FROM v_holding_live_value WHERE user_id = ?
            ORDER BY account_name, snapshot_value DESC
            """,
            (rs, row) -> new HoldingAtMarketView(rs.getLong("account_id"), rs.getString("account_name"),
                rs.getLong("security_id"), rs.getString("symbol"), rs.getString("security_name"),
                rs.getBoolean("is_cash"), rs.getObject("snapshot_as_of", LocalDate.class),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("snapshot_price"),
                rs.getBigDecimal("snapshot_value"), rs.getBigDecimal("live_price"),
                instant(rs.getObject("quote_as_of", OffsetDateTime.class)), rs.getString("quote_source"),
                rs.getBigDecimal("live_value")),
            currentUser.id());
    }

    // ---------- alerts ----------

    @GetMapping("/alerts")
    public List<AlertView> alerts() {
        return alerts.findAllForUser(currentUser.id()).stream().map(AlertView::of).toList();
    }

    @PostMapping("/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertView addAlert(@Valid @RequestBody NewAlert request) {
        return AlertView.of(market.addAlert(currentUser.id(), request.symbol(), request.rule(),
            request.threshold(), request.note()));
    }

    @PutMapping("/alerts/{id}/active")
    @Transactional
    public AlertView setActive(@PathVariable Long id, @Valid @RequestBody SetActive request) {
        var alert = alerts.findByIdAndUserId(id, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        alert.setActive(request.active());
        return AlertView.of(alerts.save(alert));
    }

    @DeleteMapping("/alerts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteAlert(@PathVariable Long id) {
        var alert = alerts.findByIdAndUserId(id, currentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        alerts.delete(alert);
    }

    /** Recent firings, newest first, with whether each was delivered. */
    @GetMapping("/alerts/events")
    public List<AlertEventView> alertEvents(@RequestParam(defaultValue = "50") int size) {
        return events.findRecentForUser(currentUser.id(), PageRequest.of(0, Math.min(size, 200)))
            .stream().map(AlertEventView::of).toList();
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    // ---------- shapes ----------

    public record Watch(@NotBlank @Size(max = 16) String symbol, @Size(max = 500) String note) {
    }

    public record NewAlert(@NotBlank @Size(max = 16) String symbol, @NotBlank String rule,
                           @NotNull BigDecimal threshold, @Size(max = 500) String note) {
    }

    public record SetActive(@NotNull Boolean active) {
    }

    public record StatusView(String provider, boolean available, String detail, boolean scheduled,
                             String refreshEvery, boolean notifierConfigured, Instant lastRefreshAt,
                             String lastRefreshOutcome) {
    }

    public record WatchlistView(Long id, Long securityId, String symbol, String name, String note,
                                Instant createdAt) {
        static WatchlistView of(WatchlistEntry w) {
            return new WatchlistView(w.getId(), w.getSecurity().getId(), w.getSecurity().getSymbol(),
                w.getSecurity().getName(), w.getNote(), w.getCreatedAt());
        }
    }

    /** @param changePct today's move against the previous close, in percent; null when unknown */
    public record QuoteView(Long securityId, String symbol, String name, BigDecimal price,
                            BigDecimal previousClose, BigDecimal changePct, Instant asOf,
                            String source, boolean watched, boolean held) {
    }

    /**
     * @param snapshotValue what the positions file said the position was worth on snapshotAsOf
     * @param liveValue quantity × latest quote; null when there is no quote or no quantity. Cash
     *     keeps its snapshot value: a dollar is worth a dollar.
     */
    public record HoldingAtMarketView(Long accountId, String accountName, Long securityId,
                                      String symbol, String securityName, boolean cash,
                                      LocalDate snapshotAsOf, BigDecimal quantity,
                                      BigDecimal snapshotPrice, BigDecimal snapshotValue,
                                      BigDecimal livePrice, Instant quoteAsOf, String quoteSource,
                                      BigDecimal liveValue) {
    }

    public record AlertView(Long id, Long securityId, String symbol, String rule,
                            BigDecimal threshold, boolean active, boolean armed,
                            Instant lastFiredAt, String note) {
        static AlertView of(PriceAlert a) {
            return new AlertView(a.getId(), a.getSecurity().getId(), a.getSecurity().getSymbol(),
                a.getRule(), a.getThreshold(), a.isActive(), a.isArmed(), a.getLastFiredAt(),
                a.getNote());
        }
    }

    public record AlertEventView(Long id, Long alertId, String symbol, String rule,
                                 BigDecimal threshold, BigDecimal price, Instant firedAt,
                                 String message, boolean delivered, String deliveryError) {
        static AlertEventView of(AlertEvent e) {
            var alert = e.getAlert();
            return new AlertEventView(e.getId(), alert.getId(), alert.getSecurity().getSymbol(),
                alert.getRule(), alert.getThreshold(),
                e.getQuote() == null ? null : e.getQuote().getPrice(), e.getFiredAt(),
                e.getMessage(), e.isDelivered(), e.getDeliveryError());
        }
    }
}
