package llc.feelingfroggy.finances.api;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reads the Flyway-managed reporting views (D-11).
 *
 * <p>Uses {@link JdbcTemplate} rather than JPA deliberately: these are set-oriented reads over
 * views, and mapping them through entities would add a round trip and an object graph nobody wants.
 * The SQL lives in {@code R__reporting_views.sql}, not in strings here.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ReportController(JdbcTemplate jdbc, CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * Net worth per entity, plus the combined figure (the row with a null entity).
     *
     * <p>Each row says how much of it rests on a holdings snapshot and how old the oldest one is.
     * A brokerage balance is the last positions import, and a deposit made since is not in the
     * figure until the next one — so the number without its date is a number that can be quietly
     * wrong by whatever moved after the snapshot.
     */
    @GetMapping("/net-worth")
    public List<NetWorthRow> netWorth() {
        return jdbc.query("""
            SELECT ledger_entity_id, net_worth, snapshot_accounts, oldest_snapshot
            FROM v_net_worth WHERE user_id = ?
            ORDER BY ledger_entity_id NULLS LAST
            """,
            (rs, row) -> new NetWorthRow(
                rs.getObject("ledger_entity_id", Long.class), rs.getBigDecimal("net_worth"),
                rs.getInt("snapshot_accounts"), rs.getObject("oldest_snapshot", LocalDate.class)),
            currentUser.id());
    }

    /** Spend against target by month. Defaults to the last twelve months. */
    @GetMapping("/spend-vs-target")
    public List<SpendRow> spendVsTarget(@RequestParam(required = false) LocalDate from,
                                        @RequestParam(required = false) LocalDate to) {
        // The configured zone, not the container's UTC — see ClockConfig.
        LocalDate start = from == null ? LocalDate.now(clock).withDayOfMonth(1).minusMonths(11) : from;
        LocalDate end = to == null ? LocalDate.now(clock) : to;

        return jdbc.query("""
            SELECT month, category_id, category_name, category_kind, ledger_entity_id,
                   net_amount, target_amount, remaining, transaction_count
            FROM v_spend_vs_target
            WHERE user_id = ? AND month >= date_trunc('month', ?::date) AND month <= ?::date
            ORDER BY month DESC, category_name
            """,
            (rs, row) -> new SpendRow(
                rs.getObject("month", LocalDate.class),
                rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getString("category_kind"),
                rs.getLong("ledger_entity_id"),
                rs.getBigDecimal("net_amount"),
                rs.getBigDecimal("target_amount"),
                rs.getBigDecimal("remaining"),
                rs.getInt("transaction_count")),
            currentUser.id(), start, end);
    }

    /**
     * What moved in each month, categorized or not: the denominator every other spend figure
     * lacks. A month where most rows are still in the review queue looked like a cheap month on
     * every screen, so this says how much is still uncategorized. Transfers are excluded, as
     * everywhere. The row with a null entity is the combined figure.
     */
    @GetMapping("/monthly-totals")
    public List<MonthlyTotalsRow> monthlyTotals(@RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(clock).withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now(clock) : to;

        return jdbc.query("""
            SELECT month, ledger_entity_id, money_out, money_in, uncategorized_out,
                   uncategorized_count, transaction_count
            FROM v_monthly_totals
            WHERE user_id = ? AND month >= date_trunc('month', ?::date) AND month <= ?::date
            ORDER BY month DESC, ledger_entity_id NULLS FIRST
            """,
            (rs, row) -> new MonthlyTotalsRow(
                rs.getObject("month", LocalDate.class),
                rs.getObject("ledger_entity_id", Long.class),
                rs.getBigDecimal("money_out"),
                rs.getBigDecimal("money_in"),
                rs.getBigDecimal("uncategorized_out"),
                rs.getInt("uncategorized_count"),
                rs.getInt("transaction_count")),
            currentUser.id(), start, end);
    }

    /**
     * Whether the ledger agrees with each statement's closing balance.
     *
     * <p>A non-zero {@code difference} means a transaction is missing, duplicated, or wrong — in
     * dollars, without a manual tally. {@code baseline} says how the computed figure was reached:
     * from the statement's own opening balance, so a difference is a real discrepancy; or by
     * summing the whole history, so a difference may only mean the account's early history was
     * never imported. The view has said this since the opening-balance work and this endpoint
     * did not pass it on, which left the dashboard's qualifier a branch that could never render.
     */
    @GetMapping("/reconciliation")
    public List<ReconciliationRow> reconciliation() {
        return jdbc.query("""
            SELECT statement_id, account_id, period_start, period_end, closing_balance,
                   computed_balance, difference, reconciled_at IS NOT NULL AS reconciled, baseline
            FROM v_statement_reconciliation
            WHERE user_id = ?
            ORDER BY period_end DESC
            """,
            (rs, row) -> new ReconciliationRow(
                rs.getLong("statement_id"),
                rs.getLong("account_id"),
                rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class),
                rs.getBigDecimal("closing_balance"),
                rs.getBigDecimal("computed_balance"),
                rs.getBigDecimal("difference"),
                rs.getBoolean("reconciled"),
                rs.getString("baseline")),
            currentUser.id());
    }

    /**
     * @param snapshotAccounts accounts in this figure valued from a holdings snapshot, not the ledger
     * @param oldestSnapshot the date of the oldest such snapshot; null when there are none
     */
    public record NetWorthRow(Long ledgerEntityId, BigDecimal netWorth, int snapshotAccounts,
                              LocalDate oldestSnapshot) {
    }

    public record SpendRow(LocalDate month, Long categoryId, String categoryName, String categoryKind,
                           Long ledgerEntityId, BigDecimal netAmount, BigDecimal targetAmount,
                           BigDecimal remaining, int transactionCount) {
    }

    /**
     * @param ledgerEntityId null on the combined row
     * @param moneyIn credits that are not transfers — income and refunds both, which is why it is
     *     not called income
     * @param uncategorizedOut debits still waiting for a category, the part of moneyOut that no
     *     spend-by-category figure includes
     */
    public record MonthlyTotalsRow(LocalDate month, Long ledgerEntityId, BigDecimal moneyOut,
                                   BigDecimal moneyIn, BigDecimal uncategorizedOut,
                                   int uncategorizedCount, int transactionCount) {
    }

    /** @param baseline {@code opening_balance} or {@code full_history} — see the method above */
    public record ReconciliationRow(Long statementId, Long accountId, LocalDate periodStart,
                                    LocalDate periodEnd, BigDecimal closingBalance,
                                    BigDecimal computedBalance, BigDecimal difference,
                                    boolean reconciled, String baseline) {
    }
}
