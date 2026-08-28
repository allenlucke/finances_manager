package llc.feelingfroggy.finances.api;

import llc.feelingfroggy.finances.domain.AppUser;
import llc.feelingfroggy.finances.repo.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Resolves the authenticated principal to the owning {@link AppUser}.
 *
 * <p>Every query in this API is scoped by the id this returns. Taking a user id from a request
 * parameter or a request body would make one person's data reachable by guessing a number, so the
 * id only ever comes from the session.
 */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    /** The signed-in user's id. */
    public Long id() {
        return entity().getId();
    }

    public AppUser entity() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByEmailIgnoreCase(authentication.getName())
            // Authenticated against a user row that no longer exists: the session outlived the
            // account. Treat as unauthenticated rather than 500.
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
