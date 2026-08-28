package llc.feelingfroggy.finances.support;

import org.springframework.boot.test.context.SpringBootTest;
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
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
