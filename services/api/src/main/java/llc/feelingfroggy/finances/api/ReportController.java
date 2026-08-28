package llc.feelingfroggy.finances.api;

import java.math.BigDecimal;
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

    public ReportController(JdbcTemplate jdbc, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    /** Net worth per entity, plus the combined figure (the row with a null entity). */
    @GetMapping("/net-worth")
    public List<NetWorthRow> netWorth() {
        return jdbc.query("""
            SELECT ledger_entity_id, net_worth FROM v_net_worth WHERE user_id = ?
            ORDER BY ledger_entity_id NULLS LAST
            """,
            (rs, row) -> new NetWorthRow(
                rs.getObject("ledger_entity_id", Long.class), rs.getBigDecimal("net_worth")),
            currentUser.id());
    }

    /** Spend against target by month. Defaults to the last twelve months. */
    @GetMapping("/spend-vs-target")
    public List<SpendRow> spendVsTarget(@RequestParam(required = false) LocalDate from,
                                        @RequestParam(required = false) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1).minusMonths(11) : from;
        LocalDate end = to == null ? LocalDate.now() : to;

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
     * Whether the ledger agrees with each statement's closing balance.
     *
     * <p>A non-zero {@code difference} means a transaction is missing, duplicated, or wrong — in
     * dollars, without a manual tally.
     */
    @GetMapping("/reconciliation")
    public List<ReconciliationRow> reconciliation() {
        return jdbc.query("""
            SELECT statement_id, account_id, period_start, period_end, closing_balance,
                   computed_balance, difference, reconciled_at IS NOT NULL AS reconciled
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
                rs.getBoolean("reconciled")),
            currentUser.id());
    }

    public record NetWorthRow(Long ledgerEntityId, BigDecimal netWorth) {
    }

    public record SpendRow(LocalDate month, Long categoryId, String categoryName, String categoryKind,
                           Long ledgerEntityId, BigDecimal netAmount, BigDecimal targetAmount,
                           BigDecimal remaining, int transactionCount) {
    }

    public record ReconciliationRow(Long statementId, Long accountId, LocalDate periodStart,
                                    LocalDate periodEnd, BigDecimal closingBalance,
                                    BigDecimal computedBalance, BigDecimal difference,
                                    boolean reconciled) {
    }
}
