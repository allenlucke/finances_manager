package llc.feelingfroggy.finances.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Net worth on each date (M9). Taken by the housekeeping tick once a day, and on request. */
@Service
public class SnapshotService {

    public record Point(LocalDate asOf, Long ledgerEntityId, BigDecimal netWorth, int snapshotAccounts) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SnapshotService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Writes today's figures from v_net_worth, replacing today's if already taken. */
    @Transactional
    public int take(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return jdbc.update("""
            INSERT INTO net_worth_snapshot (user_id, as_of, ledger_entity_id, net_worth, snapshot_accounts)
            SELECT user_id, ?, ledger_entity_id, net_worth, snapshot_accounts
            FROM v_net_worth WHERE user_id = ?
            ON CONFLICT (user_id, as_of, ledger_entity_id) DO UPDATE
                SET net_worth = EXCLUDED.net_worth, snapshot_accounts = EXCLUDED.snapshot_accounts,
                    created_at = now()
            """, today, userId);
    }

    @Transactional
    public void takeIfMissing(Long userId) {
        Integer have = jdbc.queryForObject(
            "SELECT count(*) FROM net_worth_snapshot WHERE user_id = ? AND as_of = ?",
            Integer.class, userId, LocalDate.now(clock));
        if (have == null || have == 0) {
            take(userId);
        }
    }

    /** The newest snapshot date, or null before the first one. */
    public LocalDate latestAsOf(Long userId) {
        return jdbc.query("SELECT max(as_of) AS d FROM net_worth_snapshot WHERE user_id = ?",
            rs -> rs.next() ? rs.getObject("d", LocalDate.class) : null, userId);
    }

    public List<Point> history(Long userId, int days) {
        LocalDate since = LocalDate.now(clock).minusDays(Math.min(Math.max(days, 1), 3660));
        return jdbc.query("""
            SELECT as_of, ledger_entity_id, net_worth, snapshot_accounts
            FROM net_worth_snapshot WHERE user_id = ? AND as_of >= ?
            ORDER BY as_of, ledger_entity_id NULLS FIRST
            """,
            (rs, i) -> new Point(rs.getObject("as_of", LocalDate.class),
                rs.getObject("ledger_entity_id", Long.class), rs.getBigDecimal("net_worth"),
                rs.getInt("snapshot_accounts")),
            userId, since);
    }
}
