package llc.feelingfroggy.finances.security;

import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records login attempts and locks an account after repeated failures.
 *
 * <p>Attempts go to the {@code login_attempt} table rather than the application log because
 * docs/SECURITY.md forbids account identifiers at INFO level — and because a log line cannot be
 * counted in a WHERE clause.
 *
 * <p>The lock is <em>time-based, not sticky</em>: it expires on its own once the window passes.
 * A permanent lock on a single-user system whose only administrator is the locked-out user is a
 * denial of service against yourself.
 */
@Service
public class LoginAttemptService {

    /** Enough to absorb genuine typos, few enough to make guessing useless. */
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final JdbcTemplate jdbc;

    public LoginAttemptService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Writes the attempt in a transaction of its own.
     *
     * <p>{@code REQUIRES_NEW} is load-bearing, not defensive habit. This is the same class of bug
     * that lost import failure records: an audit row written inside a transaction that later rolls
     * back disappears along with it. Here the consequence is worse than a missing log line —
     * failures are what lockout counts, so losing them means brute-force protection <em>fails
     * open</em>, silently and with no symptom until it matters.
     *
     * <p>It happens to work today because authentication events fire outside any transaction. That
     * is incidental: making a caller transactional later would break it with nothing to notice.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, boolean successful, String method, String sourceIp) {
        jdbc.update("""
            INSERT INTO login_attempt (username, successful, method, source_ip)
            VALUES (?, ?, ?, ?::inet)
            """, username, successful, method, sourceIp);
    }

    /**
     * True when this username has too many recent failures.
     *
     * <p>Counts only failures since the last <em>success</em>, so signing in successfully clears the
     * count. Otherwise five typos spread over a week would lock an account that has been used fine
     * in between.
     */
    @Transactional(readOnly = true)
    public boolean isLocked(String username) {
        Integer failures = jdbc.queryForObject("""
            SELECT count(*) FROM login_attempt
            WHERE lower(username) = lower(?)
              AND NOT successful
              AND attempted_at > now() - ?::interval
              AND attempted_at > COALESCE((
                    SELECT max(attempted_at) FROM login_attempt
                    WHERE lower(username) = lower(?) AND successful
                  ), '-infinity'::timestamptz)
            """, Integer.class, username, WINDOW.toMinutes() + " minutes", username);

        return failures != null && failures >= MAX_FAILURES;
    }
}
