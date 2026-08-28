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
     * Best effort. Behind a reverse proxy this is the proxy unless it forwards the original, and
     * the column is nullable precisely because it cannot always be known.
     */
    private static String sourceIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
