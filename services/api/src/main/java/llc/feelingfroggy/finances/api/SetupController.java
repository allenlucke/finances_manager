package llc.feelingfroggy.finances.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import llc.feelingfroggy.finances.domain.AppUser;
import llc.feelingfroggy.finances.domain.EntityKind;
import llc.feelingfroggy.finances.domain.LedgerEntity;
import llc.feelingfroggy.finances.repo.AppUserRepository;
import llc.feelingfroggy.finances.repo.LedgerEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * First-run setup: creates the initial account when the database is empty.
 *
 * <p>This is <strong>not</strong> a registration endpoint. It is permitted anonymously only while
 * {@code app_user} has no rows, and refuses once one exists — so it closes itself the moment it is
 * used and cannot be a way in later. A single-user system needs some way to create that user, and
 * this is less error-prone than a seeded password in a migration, which would be a committed
 * credential and forbidden by docs/SECURITY.md.
 *
 * <p>It also creates the two ledger entities the domain needs to be useful at all, since an account
 * cannot exist without one.
 */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {

    private final AppUserRepository users;
    private final LedgerEntityRepository entities;
    private final PasswordEncoder passwordEncoder;

    public SetupController(AppUserRepository users, LedgerEntityRepository entities,
                           PasswordEncoder passwordEncoder) {
        this.users = users;
        this.entities = entities;
        this.passwordEncoder = passwordEncoder;
    }

    /** Whether first-run setup is still available. The SPA uses this to decide what to show. */
    @GetMapping
    public SetupState state() {
        return new SetupState(users.count() == 0);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthController.MeResponse createFirstUser(@Valid @RequestBody FirstUser request) {
        if (users.count() > 0) {
            // Self-closing: once an account exists this is permanently unavailable.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Setup has already been completed");
        }

        var user = users.save(new AppUser(request.email(), request.displayName(),
            passwordEncoder.encode(request.password())));

        // An account requires an entity, so a usable system needs at least one from the start.
        entities.save(new LedgerEntity(user.getId(), "Personal", EntityKind.PERSONAL));
        entities.save(new LedgerEntity(user.getId(), "Feeling Froggy LLC", EntityKind.BUSINESS));

        return new AuthController.MeResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    public record SetupState(boolean required) {
    }

    public record FirstUser(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 120) String displayName,
        // Long rather than complex: a long passphrase beats character-class rules, and this
        // account is reachable only over Tailscale (D-16).
        @NotBlank @Size(min = 12, max = 200) String password) {
    }
}
