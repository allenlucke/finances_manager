package llc.feelingfroggy.finances.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that need the real schema.
 *
 * <p>PostgreSQL 18, matching production (D-04) — deliberately not H2. These tests exist to check
 * window functions, GROUPING SETS, a {@code daterange} exclusion constraint, and partial unique
 * indexes; an in-memory database would either reject that SQL or quietly behave differently, and a
 * test that passes against a different engine than production is worse than no test.
 *
 * <p>The container is {@code static} so one instance is shared by every subclass in the run.
 * Flyway rebuilds the schema on first use, which takes about a second; per-class containers would
 * multiply that by the number of test classes for no benefit.
 */
@SpringBootTest
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // Every distinct mock combination is its own cached Spring context with its own pool. With a
        // dozen of them, Hikari's default ten connections each blew past Postgres's hundred and the
        // late contexts failed with "too many clients already". Small pools, and a bounded context
        // cache (src/test/resources/spring.properties), keep the suite inside the limit.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 4);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 1);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Empties every application table, in one statement, regardless of what any test left behind.
     *
     * <p>Every test class used to carry its own list of {@code DELETE FROM} statements, in its own
     * order, and each list was complete only for the tables that class happened to know about.
     * The lists drifted: one class omitted {@code import_batch}, so the first test to run after a
     * class that imported anything failed on a foreign key from a table it had never heard of —
     * and whether that happened depended on which class the runner picked first. A shared
     * {@code TRUNCATE ... CASCADE} has no order to get wrong and no table to forget.
     *
     * <p>Identity sequences are left alone, matching what the DELETEs did; a test that read an id
     * back from a response keeps working, and nothing here should ever have depended on ids
     * starting at one.
     */
    protected static void cleanDatabase(JdbcTemplate jdbc) {
        jdbc.execute("""
            TRUNCATE recurring_mute, net_worth_snapshot, digest_run, notice_preference, reminder, backtest_run, strategy, trade_order_event, trade_order, alert_event, price_alert, quote, watchlist,
                     categorization, transaction, holding, security, target, statement,
                     import_batch, account, category, ledger_entity, connection, institution,
                     app_user, login_attempt, user_credentials, user_entities,
                     spring_session_attributes, spring_session
            CASCADE
            """);
    }
}
