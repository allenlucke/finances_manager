package llc.feelingfroggy.finances.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers unauthenticated and under-authenticated requests with JSON, never a redirect.
 *
 * <p>Spring Security's default is to redirect a browser to a login page. That is wrong for an SPA
 * twice over: {@code fetch} follows redirects transparently, so the app would receive an HTML page
 * with status 200 and no idea anything went wrong.
 *
 * <p>It also distinguishes two cases the SPA must handle differently:
 *
 * <ul>
 *   <li><b>{@code unauthenticated}</b> — no session. Show the login form.
 *   <li><b>{@code factor_required}</b> — signed in with a password, but a passkey is registered and
 *       has not been presented. Trigger the WebAuthn ceremony, not the login form. Sending the user
 *       back to a password prompt they already satisfied is the kind of dead end that makes people
 *       turn MFA off.
 * </ul>
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        if (needsSecondFactor()) {
            response.getWriter().write("""
                {"error":"factor_required","factor":"webauthn",\
                "detail":"A registered passkey must be presented."}""");
        } else {
            response.getWriter().write("""
                {"error":"unauthenticated","detail":"Sign in to continue."}""");
        }
    }

    /**
     * Already authenticated by some factor, so what is missing is an additional one.
     *
     * <p>Only reached for genuine authentication failures; factor-specific denials arrive at
     * {@link ApiAccessDeniedHandler}, which reads the precise decision instead of inferring it.
     */
    private static boolean needsSecondFactor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(authority ->
                FactorGrantedAuthority.PASSWORD_AUTHORITY.equals(authority.getAuthority()));
    }
}
