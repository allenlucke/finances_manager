package llc.feelingfroggy.finances.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import llc.feelingfroggy.finances.domain.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session-oriented endpoints. Login and logout are handled by Spring Security's filters
 * (see {@code SecurityConfig}); what remains is what the SPA needs around them.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final CurrentUser currentUser;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(CurrentUser currentUser, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.currentUser = currentUser;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Password login, replacing Spring Security's {@code formLogin} filter.
     *
     * <p>Owned here so every outcome is a status code and a JSON body. The filter would have
     * introduced a login-page URL, which Spring's MFA support then redirects to when a second
     * factor is missing — unusable from an SPA, since fetch follows redirects and returns HTML
     * with status 200.
     *
     * <p>Takes JSON rather than form encoding, and returns 401 without saying whether it was the
     * username or the password that was wrong — including when the account is locked, so lockout
     * cannot be used to enumerate accounts.
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody Credentials credentials,
                                      HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    credentials.username(), credentials.password()));

            // Rotate the session id on authentication. formLogin installs
            // ChangeSessionIdAuthenticationStrategy for this; a controller-based login gets
            // nothing unless it asks. Without it a session id fixed before login stays valid after
            // it, which is the textbook fixation attack — and the id is the only thing standing
            // between a stolen cookie and thirty days of access (session.timeout).
            if (request.getSession(false) != null) {
                request.changeSessionId();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            // Explicit save: since Spring Security 6 the context is not persisted automatically,
            // and without this the session would forget the login immediately.
            securityContextRepository.saveContext(context, request, response);

            return ResponseEntity.noContent().build();
        } catch (org.springframework.security.authentication.LockedException e) {
            // Named for what it is. The usual argument for hiding a lockout is that it confirms an
            // address exists — worth nothing on a single-user app bound to loopback, and the cost
            // is real: being told "not accepted" while typing the correct passphrase is
            // indistinguishable from the passphrase being wrong, so people keep trying and stay
            // locked out longer.
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        } catch (AuthenticationException e) {
            // The event publisher on the AuthenticationManager has already recorded the failure
            // for lockout purposes; nothing to add here.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Bootstraps CSRF before the first mutating request.
     *
     * <p>The SPA calls this on load: the response sets the {@code XSRF-TOKEN} cookie that Angular's
     * HttpClient then echoes back as a header. Permitted anonymously — without it, logging in would
     * itself be blocked for want of a token.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    /** Who am I? Returns 401 via the entry point when there is no session. */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        AppUser user = currentUser.entity();
        return ResponseEntity.ok(new MeResponse(user.getId(), user.getEmail(), user.getDisplayName()));
    }

    /** Never includes the password hash. */
    public record MeResponse(Long id, String email, String displayName) {
    }

    public record Credentials(@NotBlank String username, @NotBlank String password) {
    }
}
