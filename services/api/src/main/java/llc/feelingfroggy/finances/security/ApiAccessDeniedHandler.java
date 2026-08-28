package llc.feelingfroggy.finances.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.FactorAuthorizationDecision;
import org.springframework.security.authorization.RequiredFactorError;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Separates the two very different reasons a request can be denied to a signed-in user.
 *
 * <ul>
 *   <li><b>A required factor is missing or expired</b> — a passkey is registered but was not
 *       presented. That is an unfinished sign-in, not a refusal, so it answers <b>401</b> with
 *       {@code factor_required} and the SPA starts the WebAuthn ceremony.
 *   <li><b>Anything else</b> — a rejected CSRF token, or a resource this user may not have. A
 *       genuine refusal: <b>403</b>.
 * </ul>
 *
 * <p>Collapsing the two would be a real loss. Answering 401 to a CSRF failure invites the client to
 * "log in again" against a problem no credential fixes; answering 403 to a missing factor tells the
 * user they are forbidden from their own data.
 *
 * <p>The distinction is read from the {@link FactorAuthorizationDecision} on the exception rather
 * than re-derived from the session's authorities. An earlier version inferred it as "has a password
 * factor but no WebAuthn factor", which is true of <em>every</em> password-only session — so every
 * CSRF rejection was reported as a missing passkey. The authorization layer already decided; this
 * only reports what it decided.
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException denied) throws IOException {

        response.setContentType("application/json");

        String factors = missingFactors(denied);
        if (factors != null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("""
                {"error":"factor_required","factor":"%s",\
                "detail":"A registered passkey must be presented."}""".formatted(factors));
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("""
            {"error":"forbidden","detail":"This request was refused."}""");
    }

    /**
     * The factor authorities this request lacked, or null when the denial was not about factors.
     *
     * <p>Factor authorities are named {@code FACTOR_<NAME>}; the prefix is stripped so the client
     * sees {@code webauthn}.
     */
    private static String missingFactors(AccessDeniedException denied) {
        if (!(denied instanceof AuthorizationDeniedException authorizationDenied)) {
            return null;
        }
        if (!(authorizationDenied.getAuthorizationResult()
                instanceof FactorAuthorizationDecision decision)) {
            return null;
        }
        String names = decision.getFactorErrors().stream()
            .map(RequiredFactorError::getRequiredFactor)
            .map(factor -> factor.getAuthority().replaceFirst("^FACTOR_", "").toLowerCase())
            .distinct()
            .collect(Collectors.joining(","));
        return names.isBlank() ? null : names;
    }
}
