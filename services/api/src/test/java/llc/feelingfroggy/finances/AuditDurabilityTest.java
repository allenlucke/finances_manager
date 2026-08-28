package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import llc.feelingfroggy.finances.security.LoginAttemptService;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Audit records must outlive the failures they describe.
 *
 * <p>This bug class has already bitten once: import batch rows were written inside the transaction
 * that applied the rows, so a failed import rolled back the evidence of its own failure. These
 * tests pin the same guarantee everywhere it matters, because the failure mode is invisible —
 * nothing errors, a row simply is not there.
 */
@DisplayName("Audit durability")
class AuditDurabilityTest extends PostgresIntegrationTest {

    @Autowired
    private LoginAttemptService attempts;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM login_attempt");
    }

    @Test
    @DisplayName("a failed-login record survives a rollback of the surrounding transaction")
    void failedLoginRecordSurvivesRollback() {
        var template = new TransactionTemplate(transactionManager);

        try {
            template.execute(status -> {
                attempts.record("allen@feelingfroggy.llc", false, "password", "127.0.0.1");
                // Whatever the caller was doing then fails.
                throw new IllegalStateException("the surrounding work failed");
            });
        } catch (IllegalStateException expected) {
            // The throw is the setup; what matters is what survived it.
        }

        Integer recorded = jdbc.queryForObject(
            "SELECT count(*) FROM login_attempt WHERE NOT successful", Integer.class);

        // If this is 0, lockout silently counts nothing and brute-force protection fails open.
        assertThat(recorded).isEqualTo(1);
    }

    @Test
    @DisplayName("lockout still triggers when every attempt was recorded from a rolled-back caller")
    void lockoutWorksDespiteRollbacks() {
        var template = new TransactionTemplate(transactionManager);

        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                template.execute(status -> {
                    attempts.record("allen@feelingfroggy.llc", false, "password", "127.0.0.1");
                    throw new IllegalStateException("rolled back");
                });
            } catch (IllegalStateException expected) {
                // ignored
            }
        }

        assertThat(attempts.isLocked("allen@feelingfroggy.llc")).isTrue();
    }

    @Test
    @DisplayName("a successful login is recorded too, and clears the failure count")
    void successIsRecordedAndResetsTheCount() {
        for (int attempt = 0; attempt < 5; attempt++) {
            attempts.record("allen@feelingfroggy.llc", false, "password", "127.0.0.1");
        }
        assertThat(attempts.isLocked("allen@feelingfroggy.llc")).isTrue();

        attempts.record("allen@feelingfroggy.llc", true, "password", "127.0.0.1");

        // Counted only since the last success, so signing in clears the slate.
        assertThat(attempts.isLocked("allen@feelingfroggy.llc")).isFalse();
    }

    @Test
    @DisplayName("lockout is scoped to the username, not global")
    void lockoutDoesNotSpillOntoOtherAccounts() {
        for (int attempt = 0; attempt < 5; attempt++) {
            attempts.record("someone@else.com", false, "password", "127.0.0.1");
        }

        // One account being attacked must not lock everyone out.
        assertThat(attempts.isLocked("someone@else.com")).isTrue();
        assertThat(attempts.isLocked("allen@feelingfroggy.llc")).isFalse();
    }

    @Test
    @DisplayName("lockout matching is case-insensitive, like the email index")
    void lockoutIgnoresCase() {
        for (int attempt = 0; attempt < 5; attempt++) {
            attempts.record("Allen@FeelingFroggy.LLC", false, "password", "127.0.0.1");
        }

        // Otherwise changing the capitalisation of your own address would bypass the lock.
        assertThat(attempts.isLocked("allen@feelingfroggy.llc")).isTrue();
    }
}
