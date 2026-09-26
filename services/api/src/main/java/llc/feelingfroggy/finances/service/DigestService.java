package llc.feelingfroggy.finances.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.domain.DomainRuleViolation;
import llc.feelingfroggy.finances.domain.Reminder;
import llc.feelingfroggy.finances.repo.ReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What needs a look (M8, D-20).
 *
 * <p>Composed from what the system already knows — nothing here writes a ledger row or invents a
 * figure — and pushed once a day through the same notifier as price alerts. Every item is a
 * sentence with the number in it, and says where to go. The same list feeds the dashboard's
 * card, so the push and the screen never disagree.
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    public record Item(String kind, String severity, String text, String link) {
    }

    public record Preferences(boolean digestEnabled, LocalTime digestTime, int staleAfterDays,
                              int draftWaitHours, boolean quietWhenEmpty) {
        public static final Preferences DEFAULT = new Preferences(true, LocalTime.of(7, 30), 35, 24, true);
    }

    public record Run(Long id, LocalDate forDate, Instant ranAt, boolean manual, int items, boolean sent,
                      String deliveryError, String body) {
    }

    private final JdbcTemplate jdbc;
    private final ReminderRepository reminders;
    private final RecurringService recurring;
    private final Notifier notifier;
    private final Clock clock;

    public DigestService(JdbcTemplate jdbc, ReminderRepository reminders, RecurringService recurring,
                         Notifier notifier, Clock clock) {
        this.jdbc = jdbc;
        this.reminders = reminders;
        this.recurring = recurring;
        this.notifier = notifier;
        this.clock = clock;
    }

    // --- preferences -----------------------------------------------------------------------

    public Preferences preferences(Long userId) {
        var rows = jdbc.query("""
            SELECT digest_enabled, digest_time, stale_after_days, draft_wait_hours, quiet_when_empty
            FROM notice_preference WHERE user_id = ?
            """,
            (rs, i) -> new Preferences(rs.getBoolean("digest_enabled"),
                rs.getObject("digest_time", LocalTime.class), rs.getInt("stale_after_days"),
                rs.getInt("draft_wait_hours"), rs.getBoolean("quiet_when_empty")),
            userId);
        return rows.isEmpty() ? Preferences.DEFAULT : rows.get(0);
    }

    @Transactional
    public Preferences savePreferences(Long userId, Preferences p) {
        if (p.staleAfterDays() < 1 || p.staleAfterDays() > 365) {
            throw new DomainRuleViolation("Stale after is between 1 and 365 days");
        }
        if (p.draftWaitHours() < 1 || p.draftWaitHours() > 720) {
            throw new DomainRuleViolation("Draft wait is between 1 and 720 hours");
        }
        jdbc.update("""
            INSERT INTO notice_preference (user_id, digest_enabled, digest_time, stale_after_days,
                                           draft_wait_hours, quiet_when_empty)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET digest_enabled = EXCLUDED.digest_enabled,
                digest_time = EXCLUDED.digest_time, stale_after_days = EXCLUDED.stale_after_days,
                draft_wait_hours = EXCLUDED.draft_wait_hours, quiet_when_empty = EXCLUDED.quiet_when_empty
            """,
            userId, p.digestEnabled(), p.digestTime() == null ? LocalTime.of(7, 30) : p.digestTime(),
            p.staleAfterDays(), p.draftWaitHours(), p.quietWhenEmpty());
        return preferences(userId);
    }

    // --- composing -------------------------------------------------------------------------

    /** Everything that needs a look right now, worst first. */
    public List<Item> compose(Long userId) {
        Preferences p = preferences(userId);
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        var items = new ArrayList<Item>();
        items.addAll(mismatchedStatements(userId));
        items.addAll(troubledOrders(userId, now, p));
        items.addAll(dueReminders(userId, today));
        items.addAll(overBudget(userId, today));
        items.addAll(staleAccounts(userId, today, p));
        items.addAll(undeliveredAlerts(userId, now));
        items.addAll(strategiesInTrouble(userId));
        items.addAll(cashflow(userId));
        return items;
    }

    /** What the ledger's rhythm says: a charge that stopped, a duplicate, a spike, the week ahead. */
    private List<Item> cashflow(Long userId) {
        var items = new ArrayList<Item>();
        RecurringService.Report report = recurring.report(userId, 7);
        for (var a : report.anomalies()) {
            items.add(new Item(a.kind(), a.severity(), a.text(), "/cashflow"));
        }
        for (var s : report.series()) {
            if ("missing".equals(s.status())) {
                items.add(new Item("missing_charge", "medium", s.label() + " (" + dollars(s.typicalAmount()) + " "
                    + s.cadence() + ") has not appeared since " + s.lastDate() + "; expected around "
                    + s.nextExpected(), "/cashflow"));
            }
        }
        if (report.runwayWeeks() != null && report.runwayWeeks().compareTo(new BigDecimal("4")) < 0) {
            items.add(new Item("runway", report.runwayWeeks().compareTo(new BigDecimal("2")) < 0 ? "high" : "medium",
                "Cash on hand (" + dollars(report.liquidCash()) + ") covers about " + report.runwayWeeks().toPlainString()
                    + " weeks of recurring charges at " + dollars(report.monthlyRecurringOut()) + " a month", "/cashflow"));
        }
        if (!report.upcoming().isEmpty()) {
            long debits = report.upcoming().stream().filter(e -> "debit".equals(e.direction())).count();
            if (debits > 0) {
                items.add(new Item("upcoming", "low", debits + " recurring charge" + (debits == 1 ? "" : "s")
                    + " expected in the next 7 days, about " + dollars(report.expectedOut()), "/cashflow"));
            }
        }
        return items;
    }

    private List<Item> mismatchedStatements(Long userId) {
        return jdbc.query("""
            SELECT r.statement_id, a.name, r.period_end, r.difference
            FROM v_statement_reconciliation r JOIN account a ON a.id = r.account_id
            WHERE r.user_id = ? AND r.difference <> 0 AND r.baseline = 'opening_balance'
              AND r.reconciled_at IS NULL
            ORDER BY r.period_end DESC
            """,
            (rs, i) -> new Item("reconciliation", "high",
                rs.getString("name") + ": the statement ending " + rs.getObject("period_end", LocalDate.class)
                    + " disagrees with the ledger by " + dollars(rs.getBigDecimal("difference")),
                "/dashboard"),
            userId);
    }

    private List<Item> troubledOrders(Long userId, Instant now, Preferences p) {
        var items = new ArrayList<Item>();
        Timestamp waitedSince = Timestamp.from(now.minus(Duration.ofHours(p.draftWaitHours())));
        items.addAll(jdbc.query("""
            SELECT o.id, o.status, o.side, o.quantity, s.symbol, o.venue, o.created_at, o.confirmed_at, o.last_error
            FROM trade_order o JOIN security s ON s.id = o.security_id
            WHERE o.user_id = ? AND (
                  (o.status = 'draft' AND o.created_at < ?)
               OR (o.status = 'confirmed' AND o.venue = 'manual' AND o.confirmed_at < ?)
               OR (o.status IN ('failed', 'rejected') AND o.closed_at > ?))
            ORDER BY o.id
            """,
            (rs, i) -> {
                String what = rs.getString("side") + " " + rs.getBigDecimal("quantity").stripTrailingZeros().toPlainString()
                    + " " + rs.getString("symbol");
                String status = rs.getString("status");
                String text;
                if ("draft".equals(status)) {
                    long hours = Duration.between(rs.getObject("created_at", OffsetDateTime.class).toInstant(), now).toHours();
                    text = "Draft order #" + rs.getLong("id") + " (" + what + ") has waited " + hours + " hours for a decision";
                } else if ("confirmed".equals(status)) {
                    text = "Order #" + rs.getLong("id") + " (" + what + ") is confirmed for Fidelity but not marked placed";
                } else {
                    text = "Order #" + rs.getLong("id") + " (" + what + ") was " + status
                        + (rs.getString("last_error") == null ? "" : ": " + rs.getString("last_error"));
                }
                return new Item("order", "draft".equals(status) ? "medium" : "high", text, "/markets");
            },
            userId, waitedSince, waitedSince, Timestamp.from(now.minus(Duration.ofHours(24)))));
        return items;
    }

    private List<Item> dueReminders(Long userId, LocalDate today) {
        var items = new ArrayList<Item>();
        for (Reminder r : reminders.findByUserIdAndActiveTrueOrderByDueOnAsc(userId)) {
            if (r.isDue(today)) {
                items.add(new Item("reminder", r.getDueOn().isBefore(today) ? "high" : "medium",
                    r.describe(today), "/dashboard"));
            }
        }
        return items;
    }

    private List<Item> overBudget(Long userId, LocalDate today) {
        return jdbc.query("""
            SELECT category_name, net_amount, target_amount, remaining
            FROM v_spend_vs_target
            WHERE user_id = ? AND month = date_trunc('month', ?::date) AND category_kind = 'expense'
              AND remaining IS NOT NULL AND remaining < 0
            ORDER BY remaining
            """,
            (rs, i) -> new Item("budget", "medium",
                rs.getString("category_name") + " is " + dollars(rs.getBigDecimal("remaining").negate())
                    + " over its " + dollars(rs.getBigDecimal("target_amount")) + " target this month",
                "/budget"),
            userId, today);
    }

    private List<Item> staleAccounts(Long userId, LocalDate today, Preferences p) {
        return jdbc.query("""
            SELECT a.name,
                   GREATEST((SELECT max(period_end) FROM statement s WHERE s.account_id = a.id),
                            (SELECT max(as_of) FROM holding h WHERE h.account_id = a.id)) AS last_seen
            FROM account a
            WHERE a.user_id = ? AND a.is_active
            ORDER BY a.name
            """,
            (rs, i) -> {
                LocalDate lastSeen = rs.getObject("last_seen", LocalDate.class);
                if (lastSeen == null) {
                    return null;
                }
                long days = ChronoUnit.DAYS.between(lastSeen, today);
                if (days < p.staleAfterDays()) {
                    return null;
                }
                return new Item("stale", "low", rs.getString("name") + ": nothing imported since "
                    + lastSeen + " (" + days + " days)", "/import");
            },
            userId).stream().filter(java.util.Objects::nonNull).toList();
    }

    private List<Item> undeliveredAlerts(Long userId, Instant now) {
        Integer count = jdbc.queryForObject("""
            SELECT count(*) FROM alert_event WHERE user_id = ? AND delivered = false AND fired_at > ?
            """, Integer.class, userId, Timestamp.from(now.minus(Duration.ofHours(24))));
        if (count == null || count == 0) {
            return List.of();
        }
        return List.of(new Item("alerts", "low",
            count + " price alert" + (count == 1 ? "" : "s") + " fired in the last day but could not be delivered",
            "/markets"));
    }

    private List<Item> strategiesInTrouble(Long userId) {
        return jdbc.query("""
            SELECT name, last_evaluation FROM strategy
            WHERE user_id = ? AND active AND last_evaluation LIKE 'could not evaluate%'
            ORDER BY name
            """,
            (rs, i) -> new Item("strategy", "medium",
                "Strategy \"" + rs.getString("name") + "\" " + rs.getString("last_evaluation"), "/strategies"),
            userId);
    }

    // --- sending ---------------------------------------------------------------------------

    /**
     * Composes and pushes. Recorded whether or not anything went out, so "did it run today" has an
     * answer. Returns the run.
     */
    @Transactional
    public Run send(Long userId, boolean manual) {
        Preferences p = preferences(userId);
        List<Item> items = compose(userId);
        LocalDate today = LocalDate.now(clock);
        String body = items.isEmpty() ? "All quiet: nothing needs a look."
            : items.stream().map(i -> "• " + i.text()).reduce((a, b) -> a + "\n" + b).orElse("");
        boolean sent = false;
        String error = null;
        if (items.isEmpty() && p.quietWhenEmpty() && !manual) {
            error = null;
        } else if (!notifier.configured()) {
            error = "No notification channel is configured (NTFY_URL is blank).";
        } else {
            try {
                notifier.send(items.isEmpty() ? "Finances: all quiet"
                    : "Finances: " + items.size() + " thing" + (items.size() == 1 ? "" : "s") + " need a look", body);
                sent = true;
            } catch (Notifier.NotificationFailed e) {
                error = e.getMessage();
                log.warn("digest not delivered: {}", e.getMessage());
            }
        }
        Long id = jdbc.queryForObject("""
            INSERT INTO digest_run (user_id, for_date, manual, items, sent, delivery_error, body)
            VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, userId, today, manual, items.size(), sent, error, body);
        return new Run(id, today, Instant.now(clock), manual, items.size(), sent, error, body);
    }

    public List<Run> runs(Long userId, int size) {
        return jdbc.query("""
            SELECT id, for_date, ran_at, manual, items, sent, delivery_error, body
            FROM digest_run WHERE user_id = ? ORDER BY ran_at DESC LIMIT ?
            """,
            (rs, i) -> new Run(rs.getLong("id"), rs.getObject("for_date", LocalDate.class),
                rs.getObject("ran_at", OffsetDateTime.class).toInstant(), rs.getBoolean("manual"),
                rs.getInt("items"), rs.getBoolean("sent"), rs.getString("delivery_error"), rs.getString("body")),
            userId, Math.min(Math.max(size, 1), 100));
    }

    /** Whether the timer should send now: enabled, past the chosen time, and not yet today. */
    public boolean dueForAutomaticSend(Long userId) {
        Preferences p = preferences(userId);
        if (!p.digestEnabled()) {
            return false;
        }
        var nowLocal = java.time.LocalDateTime.now(clock);
        if (nowLocal.toLocalTime().isBefore(p.digestTime())) {
            return false;
        }
        Integer already = jdbc.queryForObject(
            "SELECT count(*) FROM digest_run WHERE user_id = ? AND for_date = ? AND manual = false",
            Integer.class, userId, nowLocal.toLocalDate());
        return already == null || already == 0;
    }

    // --- reminders -------------------------------------------------------------------------

    public List<Reminder> reminders(Long userId) {
        return reminders.findByUserIdOrderByActiveDescDueOnAsc(userId);
    }

    @Transactional
    public Reminder addReminder(Long userId, String title, String notes, LocalDate dueOn, String cadence,
                                Integer leadDays, BigDecimal amount) {
        return reminders.save(new Reminder(userId, title, notes, dueOn, cadence, leadDays, amount));
    }

    @Transactional
    public Reminder updateReminder(Long userId, Long id, Map<String, Object> ignored, String title, String notes,
                                   LocalDate dueOn, String cadence, Integer leadDays, BigDecimal amount,
                                   Boolean active) {
        var r = reminders.findByIdAndUserId(id, userId).orElseThrow(() -> new DomainRuleViolation("No such reminder"));
        r.update(title, notes, dueOn, cadence, leadDays, amount, active);
        return reminders.save(r);
    }

    @Transactional
    public Reminder completeReminder(Long userId, Long id) {
        var r = reminders.findByIdAndUserId(id, userId).orElseThrow(() -> new DomainRuleViolation("No such reminder"));
        r.complete(LocalDate.now(clock));
        return reminders.save(r);
    }

    @Transactional
    public void deleteReminder(Long userId, Long id) {
        reminders.delete(reminders.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new DomainRuleViolation("No such reminder")));
    }

    private static String dollars(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
