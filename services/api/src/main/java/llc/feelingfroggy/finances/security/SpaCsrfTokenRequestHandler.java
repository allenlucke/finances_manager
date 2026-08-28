package llc.feelingfroggy.finances.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Makes Spring Security's BREACH-protected CSRF tokens work with a JavaScript SPA.
 *
 * <p>The two mechanisms pull in opposite directions. BREACH protection XORs the token with a random
 * mask per response, so the value in the cookie differs every time — but Angular's
 * {@code HttpClient} reads the raw {@code XSRF-TOKEN} cookie and echoes it back verbatim in
 * {@code X-XSRF-TOKEN}. Using the XOR handler for both directions rejects those requests.
 *
 * <p>So: render through the XOR handler (the cookie stays BREACH-protected), but resolve a
 * <em>header</em> as the plain token, since that is what the browser actually sends. This is the
 * pattern from the Spring Security reference guide.
 *
 * <p>Relevant because D-12 chose session cookies over bearer tokens — cookies are automatically
 * attached by the browser, which is exactly what CSRF exploits, so this protection is load-bearing
 * rather than ceremonial.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // Force the token to be loaded now so the cookie is written on this response.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        // A header came from JavaScript reading the cookie: it is the raw token.
        // A parameter came from a form rendered by Spring: it is XOR-masked.
        return StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))
            ? plain.resolveCsrfTokenValue(request, csrfToken)
            : xor.resolveCsrfTokenValue(request, csrfToken);
    }
}
