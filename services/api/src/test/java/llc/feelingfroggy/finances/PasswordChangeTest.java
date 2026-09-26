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

/** The one way a passphrase changes: signed in, proving the current one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Changing the passphrase")
class PasswordChangeTest extends PostgresIntegrationTest {

    private static final String OLD = "a-long-enough-passphrase";
    private static final String NEW = "an-even-longer-new-passphrase";

    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid", "displayName", "Owner", "password", OLD));
        api.login("owner@finances.invalid", OLD);
    }

    @Test
    @DisplayName("a wrong current passphrase, a short new one, or the same one again are refused in words")
    void refusals() {
        var wrong = api.putJson("/api/v1/auth/password", Map.of("currentPassword", "not-it-at-all-really", "newPassword", NEW));
        var shortOne = api.putJson("/api/v1/auth/password", Map.of("currentPassword", OLD, "newPassword", "short"));
        var same = api.putJson("/api/v1/auth/password", Map.of("currentPassword", OLD, "newPassword", OLD));

        assertThat(wrong.status()).isEqualTo(422);
        assertThat(wrong.body()).contains("current passphrase was not accepted");
        assertThat(shortOne.status()).isIn(400, 422);
        assertThat(same.status()).isEqualTo(422);
        assertThat(same.body()).contains("same as the current one");
        // Nothing changed: the old one still signs in. (Sign-out discards the CSRF cookie; prime again.)
        api.postJson("/api/v1/auth/logout", Map.of());
        api.primeCsrf();
        assertThat(api.login("owner@finances.invalid", OLD).status()).isEqualTo(204);
    }

    @Test
    @DisplayName("after a change, only the new passphrase signs in")
    void changed() {
        var changed = api.putJson("/api/v1/auth/password", Map.of("currentPassword", OLD, "newPassword", NEW));
        assertThat(changed.status()).as(changed.body()).isEqualTo(204);

        api.postJson("/api/v1/auth/logout", Map.of());
        api.primeCsrf();
        assertThat(api.login("owner@finances.invalid", OLD).status()).isEqualTo(401);
        assertThat(api.login("owner@finances.invalid", NEW).status()).isEqualTo(204);
    }
}
