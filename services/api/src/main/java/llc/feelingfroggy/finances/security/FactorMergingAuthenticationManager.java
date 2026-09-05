package llc.feelingfroggy.finances.security;

import java.util.LinkedHashSet;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Keeps the first factor when the second one authenticates.
 *
 * <p>Presenting a passkey to {@code /login/webauthn} runs through
 * {@code AbstractAuthenticationProcessingFilter}, which on success creates an <em>empty</em>
 * security context, puts the new authentication in it and saves that over the session. The new
 * authentication carries {@code FACTOR_WEBAUTHN} and nothing about the password that was accepted
 * a moment earlier — so the session that had one factor still has one factor, just a different one,
 * and {@link PasskeyMfaConfig} keeps refusing it. Observed end to end on 2026-09-05: passkey
 * accepted, then every request answered with "factor required: password". Second-factor sign-in
 * had never worked; only a browser test running the real ceremony could show it.
 *
 * <p>This wraps the WebAuthn filter's authentication manager. When the delegate succeeds and the
 * request already carried a genuine (not anonymous) authentication for the <em>same</em> user, the
 * result is rebuilt with the union of both sets of authorities, so the factors accumulate. A
 * different user, or no prior authentication, is left exactly as the delegate returned it: a
 * passkey presented cold is a single-factor sign-in and must not inherit anything.
 */
public final class FactorMergingAuthenticationManager implements AuthenticationManager {

    private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

    private final AuthenticationManager delegate;

    public FactorMergingAuthenticationManager(AuthenticationManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Authentication authenticate(Authentication request) throws AuthenticationException {
        // Read before delegating: nothing here has replaced the context yet, so this is whatever
        // the session already established — the password authentication, if there is one.
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        Authentication result = delegate.authenticate(request);
        if (result == null || !worthMerging(existing, result)) {
            return result;
        }
        var merged = new LinkedHashSet<GrantedAuthority>(result.getAuthorities());
        merged.addAll(existing.getAuthorities());
        return result.toBuilder()
            .authorities(authorities -> {
                authorities.clear();
                authorities.addAll(merged);
            })
            .build();
    }

    private static boolean worthMerging(Authentication existing, Authentication result) {
        return existing != null
            && existing.isAuthenticated()
            && !TRUST.isAnonymous(existing)
            && existing.getName() != null
            && existing.getName().equalsIgnoreCase(result.getName());
    }
}
