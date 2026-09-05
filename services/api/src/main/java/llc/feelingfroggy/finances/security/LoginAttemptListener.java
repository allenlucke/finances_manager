package llc.feelingfroggy.finances.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes an audit row for every authentication outcome.
 *
 * <p>Listening to Spring Security's events rather than instrumenting the login endpoint means
 * passkey ceremonies and any future factor are covered without touching this class again.
 */
@Component
public class LoginAttemptListener {

    private final LoginAttemptService attempts;

    public LoginAttemptListener(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        attempts.record(event.getAuthentication().getName(), true, method(event.getAuthentication()),
            sourceIp());
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        attempts.record(principal == null ? "unknown" : principal.toString(), false,
            method(event.getAuthentication()), sourceIp());
    }

    private static String method(org.springframework.security.core.Authentication authentication) {
        boolean webauthn = authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().contains("WEBAUTHN"));
        return webauthn ? "passkey" : "password";
    }

    /**
     * The connection's own address, and deliberately nothing the caller can write.
     *
     * <p>This used to prefer {@code X-Forwarded-For}. That header is supplied by whoever is
     * connecting, and it is inserted into an {@code inet} column — so a value like {@code nope}
     * made the insert throw, inside the synchronous event listener, before the failure row was
     * written. Lockout counts rows. Five wrong passwords sent with a junk header, then the right
     * one: signed in. Reproduced 2026-08-29. The header also let anyone forge the source address in
     * the security audit, and it contradicted {@code LocalTokenAuthenticationFilter}, which refuses
     * to read it for exactly this reason.
     *
     * <p>Behind a reverse proxy this records the proxy. That is a known, honest limitation and the
     * column is nullable for it; a trusted-proxy configuration can restore the original address
     * later, from the proxy's side, without ever trusting the client's.
     */
    private static String sourceIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
