package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The local token is off unless configured — asserted, not assumed.
 *
 * <p>Worth its own context because the dangerous version of this feature is the one that quietly
 * works when nobody asked for it. The default in {@code application.yml} is blank, and this pins
 * that a blank token authenticates nothing rather than matching an empty bearer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Local token, unconfigured")
class LocalTokenDisabledTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    @BeforeEach
    void reset() {
        cleanDatabase(jdbc);

        var api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
    }

    @Test
    @DisplayName("an empty bearer token authenticates nothing")
    void emptyBearerIsNotAMatch() {
        // The failure this guards against: the configured token is "" and the presented token is
        // "", so a naive byte comparison succeeds and every caller is the owner. The feature flag
        // has to be checked before the comparison, not after.
        assertThat(new ApiClient(port).bearer("").get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("no token is accepted at all while none is configured")
    void anyTokenIsRefused() {
        assertThat(new ApiClient(port).bearer("test-token-that-is-long-enough-to-be-accepted")
            .get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("cookie sessions still work, and still need CSRF")
    void theNormalPathIsUnaffected() {
        var browser = new ApiClient(port);
        browser.primeCsrf();
        assertThat(browser.login("owner@finances.invalid", "a-long-enough-passphrase").status())
            .isEqualTo(204);
        assertThat(browser.get("/api/v1/auth/me").status()).isEqualTo(200);

        // The CSRF exemption is keyed on the Authorization header, so it must not have loosened
        // anything for ordinary cookie-authenticated requests.
        assertThat(browser.postJsonWithoutCsrf("/api/v1/entities", Map.of("name", "Nope")).status())
            .isEqualTo(403);
    }
}
