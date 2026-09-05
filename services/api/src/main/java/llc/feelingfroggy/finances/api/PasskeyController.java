package llc.feelingfroggy.finances.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lets the signed-in user see and remove their registered passkeys.
 *
 * <p>Registration itself is handled by Spring Security's WebAuthn filters; this covers the
 * management that has to exist alongside it — you cannot be asked to rely on a second factor with
 * no way to see what is enrolled or to remove a lost device.
 *
 * <p>Reads Spring Security's own tables directly (see V3): they are its schema, not ours, so
 * wrapping them in JPA entities would create a second definition of the same rows that could drift.
 *
 * <p>Note that removing the last passkey returns the account to single-factor — see
 * {@code PasskeyMfaConfig} for why that is the intended recovery path rather than an oversight.
 */
@RestController
@RequestMapping("/api/v1/passkeys")
public class PasskeyController {

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;

    public PasskeyController(JdbcTemplate jdbc, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<PasskeyView> list() {
        String username = currentUser.entity().getEmail();
        return jdbc.query("""
            SELECT c.credential_id, c.label, c.created, c.last_used, c.backup_state
            FROM user_credentials c
            JOIN user_entities e ON e.id = c.user_entity_user_id
            WHERE lower(e.name) = lower(?)
            ORDER BY c.created DESC
            """,
            (rs, row) -> new PasskeyView(
                rs.getString("credential_id"),
                rs.getString("label"),
                instant(rs.getObject("created", OffsetDateTime.class)),
                instant(rs.getObject("last_used", OffsetDateTime.class)),
                rs.getBoolean("backup_state")),
            username);
    }

    /**
     * TIMESTAMPTZ arrives as an OffsetDateTime; the Postgres driver refuses to hand it over as an
     * Instant ("conversion to class java.time.Instant from timestamptz not supported"). Reading it
     * as Instant threw on the first row, which turned into a 409 — and since the mapper only runs
     * when a passkey exists, the listing worked perfectly right up until there was something to
     * list. Found by the browser test that registers one.
     */
    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    @DeleteMapping("/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String credentialId) {
        String username = currentUser.entity().getEmail();
        // Scoped by username in the DELETE itself, so one user cannot remove another's credential
        // by supplying its id.
        int removed = jdbc.update("""
            DELETE FROM user_credentials
            WHERE credential_id = ?
              AND user_entity_user_id IN (SELECT id FROM user_entities WHERE lower(name) = lower(?))
            """, credentialId, username);

        if (removed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * {@code backedUp} tells the user whether losing this device loses the passkey — a synced
     * platform passkey survives, a device-bound one does not.
     */
    public record PasskeyView(String credentialId, String label, Instant created, Instant lastUsed,
                              boolean backedUp) {
    }
}
