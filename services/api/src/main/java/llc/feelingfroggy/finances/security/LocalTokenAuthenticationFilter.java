package llc.feelingfroggy.finances.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import llc.feelingfroggy.finances.repo.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates local automation — the MCP server, and therefore Claude Code — as the owner (D-17).
 *
 * <p>Reads {@code Authorization: Bearer <token>}. On a match the request proceeds as the account
 * owner; on anything else the filter does nothing at all and the request carries on to the normal
 * session-cookie path. A wrong token is deliberately <em>not</em> a rejection here: this filter is
 * additive, and failing loudly would break browser requests that legitimately carry no token.
 *
 * <p><strong>Loopback is enforced before the token is even compared.</strong> Ordering matters — it
 * means a remote caller cannot use response timing to learn anything about the token, because no
 * comparison happens for them at all.
 *
 * <p>The comparison itself is {@link MessageDigest#isEqual} over raw bytes rather than
 * {@code String.equals}, which returns as soon as two characters differ and so leaks the length of
 * the matching prefix to anyone able to measure it.
 */
public class LocalTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LocalTokenAuthenticationFilter.class);
    private static final String PREFIX = "Bearer ";
    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private final LocalTokenProperties properties;
    private final AppUserRepository users;
    private final byte[] expected;

    public LocalTokenAuthenticationFilter(LocalTokenProperties properties, AppUserRepository users) {
        this.properties = properties;
        this.users = users;
        this.expected = properties.value().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The factors this token stands in for.
     *
     * <p>It asserts WEBAUTHN as well as PASSWORD, which it must in order to reach an API that
     * requires both once a passkey is registered (D-12) — and that is precisely the concession
     * being made. BEARER is included so the granted authorities describe how the caller actually
     * authenticated, rather than claiming a passkey ceremony that never happened.
     */
    private static List<GrantedAuthority> authorities() {
        return List.of(
            new SimpleGrantedAuthority("ROLE_USER"),
            FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.BEARER_AUTHORITY),
            FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
            FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));
    }

    /**
     * Runs on the ERROR dispatch too, which the base class skips by default.
     *
     * <p>Without this, every 4xx a controller raises came back to a token-authenticated caller as
     * <strong>401</strong>. Spring sends the error through the filter chain a second time; this
     * filter opted out of that pass, the security context had already been cleared, and the
     * authorization filter saw an anonymous request to {@code /error}. So "no such transaction"
     * arrived looking like "your token is wrong" — which sends you to check the one thing that was
     * never the problem. Found by calling restore twice and getting 401 instead of 404.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (properties.enabled() && notAlreadyAuthenticated()) {
            if (isFromThisMachine(request)) {
                presentedToken(request).ifPresent(this::authenticateIfItMatches);
            } else {
                presentedToken(request).ifPresent(ignored -> warnAboutRemoteAddress(request));
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * True when nothing has genuinely authenticated this request yet.
     *
     * <p>Not simply a null check. {@code AnonymousAuthenticationFilter} populates the context with
     * an {@code AnonymousAuthenticationToken} for every unauthenticated request, so the context is
     * almost never null by the time any later filter runs — a null check here silently did nothing
     * and every token request came back 401. The trust resolver is what tells "nobody" apart from
     * "somebody", and it also means a real session already in place is never overwritten.
     */
    private boolean notAlreadyAuthenticated() {
        var existing = SecurityContextHolder.getContext().getAuthentication();
        return existing == null || TRUST_RESOLVER.isAnonymous(existing) || !existing.isAuthenticated();
    }

    /**
     * Says why a token was ignored, because the alternative is a silent 401.
     *
     * <p>The case this exists for: running the API in Docker. A request published to
     * {@code 127.0.0.1:8080} on the host arrives having been NAT'd, so {@code getRemoteAddr()} is
     * the bridge gateway and the loopback check cannot pass — the caller genuinely is on this
     * machine, but nothing in-process can see that. Without this line the symptom is a 401 that
     * looks exactly like a wrong token, and the token is the first thing anyone re-checks.
     *
     * <p>Logged at WARN and rate-limited to once per distinct address, so a misconfigured caller
     * cannot fill the log. The address is not a secret and the token is never logged.
     */
    private void warnAboutRemoteAddress(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        // Bounded. "Once per address" is the intent; a set that keeps every address ever seen is
        // its own slow leak behind a proxy or a rotating source. Past the cap it forgets and
        // starts over, which at worst repeats a warning.
        if (warnedAbout.size() > 256) {
            warnedAbout.clear();
        }
        if (warnedAbout.add(address)) {
            log.warn("""
                A local API token was presented from {}, which is not a loopback address, so it was \
                ignored. If the API is running in a container this is expected: Docker rewrites the \
                source address, so the check cannot see that the caller is on this machine. Set \
                LOCAL_API_TOKEN_LOOPBACK_ONLY=false there — reachability is already limited by \
                publishing the port on 127.0.0.1 only. See docs/SECURITY.md.""", address);
        }
    }

    private final java.util.Set<String> warnedAbout = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private Optional<byte[]> presentedToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8));
    }

    private void authenticateIfItMatches(byte[] presented) {
        if (!MessageDigest.isEqual(expected, presented)) {
            return;
        }
        // Exactly one user exists by construction — SetupController refuses to create a second —
        // so "the owner" is unambiguous. If that ever stops being true this has to become an
        // explicit choice rather than a silently picked row, hence the guard over a findFirst().
        var all = users.findAll();
        if (all.size() != 1) {
            log.warn("Local token presented but there are {} users; refusing to guess an owner.",
                all.size());
            return;
        }
        var owner = all.getFirst();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(owner.getEmail(), null, authorities()));
    }

    /**
     * True only for a connection that originated on this machine.
     *
     * <p>Deliberately reads {@code getRemoteAddr()} and never {@code X-Forwarded-For}: that header
     * is caller-supplied, so trusting it would let anyone claim to be loopback and turn this into a
     * remotely usable bypass. Behind a real proxy this filter simply stops matching, which is the
     * safe direction in which to fail.
     */
    private boolean isFromThisMachine(HttpServletRequest request) {
        if (!properties.loopbackOnly()) {
            return true;
        }
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
