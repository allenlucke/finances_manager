package llc.feelingfroggy.finances.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationManagerFactories;
import org.springframework.security.authorization.AuthorizationManagerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * Makes a passkey a <strong>required second factor as soon as one is registered</strong> (D-12).
 *
 * <p>The escalation is automatic and that is the whole design. A configuration flag would have to be
 * turned on by hand at exactly the right moment: too early and you lock yourself out of an account
 * with no passkey; too late and the app sat on the internet with single-factor auth. Deriving the
 * requirement from the data removes both failure modes —
 *
 * <ul>
 *   <li><b>No passkey registered:</b> password alone is sufficient, which it has to be, because
 *       enrolling the first passkey requires being signed in.
 *   <li><b>A passkey registered:</b> both {@code FACTOR_PASSWORD} and {@code FACTOR_WEBAUTHN} are
 *       required. A password-only session can still authenticate, but is not authorized for the
 *       API, so it gets a 403 and the SPA prompts for the passkey.
 * </ul>
 *
 * <p>Deleting your last passkey therefore drops you back to single factor. That is deliberate — it
 * is the recovery path when every authenticator is lost, and it requires an authenticated session
 * to do. Combined with D-16 (reachable only over Tailscale) the residual risk is acceptable; if
 * this app is ever exposed publicly, that trade should be revisited.
 *
 * <p>Spring Security ships an equivalent internal arrangement, but its
 * {@code WhenWebAuthnRegisteredMfaConfiguration} is package-private, so the predicate is expressed
 * here instead of reached for.
 */
@Configuration
public class PasskeyMfaConfig {

    @Bean
    AuthorizationManagerFactory<RequestAuthorizationContext> multiFactorAuthorization(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository credentials) {

        return AuthorizationManagerFactories.<RequestAuthorizationContext>multiFactor()
            .when(authentication -> hasRegisteredPasskey(authentication, userEntities, credentials))
            .requireFactors(
                FactorGrantedAuthority.PASSWORD_AUTHORITY,
                FactorGrantedAuthority.WEBAUTHN_AUTHORITY)
            .build();
    }

    private static boolean hasRegisteredPasskey(Authentication authentication,
                                                PublicKeyCredentialUserEntityRepository userEntities,
                                                UserCredentialRepository credentials) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        PublicKeyCredentialUserEntity user = userEntities.findByUsername(authentication.getName());
        if (user == null) {
            return false;
        }
        return !credentials.findByUserId(user.getId()).isEmpty();
    }
}
