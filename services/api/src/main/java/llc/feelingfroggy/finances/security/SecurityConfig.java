package llc.feelingfroggy.finances.security;

import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import jakarta.servlet.http.HttpServletRequest;
import llc.feelingfroggy.finances.repo.AppUserRepository;

/**
 * Authentication per D-12: self-hosted, server-side session cookies, WebAuthn passkeys.
 *
 * <p><strong>Sessions, not JWTs.</strong> One API process and one same-origin SPA means a JWT's
 * statelessness buys nothing, while its cost — you cannot revoke one — is real for a system holding
 * bank tokens. Sessions are persisted to Postgres by Spring Session, so "log out everywhere" is a
 * DELETE and a restart does not sign anyone out.
 *
 * <p><strong>Ordering of factors.</strong> Password is the first factor and passkeys are enrolled
 * from inside an authenticated session. That ordering is forced by reality: you cannot require a
 * second factor before the user owns one. Once a passkey is registered, promoting it from an
 * alternative to a required second factor is a configuration change (Spring Security ships
 * {@code WhenWebAuthnRegisteredMfaConfiguration} for exactly this) and is the follow-up before this
 * app is ever reachable from anything but Tailscale — see docs/SECURITY.md and D-16.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SecurityProperties properties;
    private final LocalTokenProperties localToken;
    private final AppUserRepository users;

    public SecurityConfig(SecurityProperties properties, LocalTokenProperties localToken,
                          AppUserRepository users) {
        this.properties = properties;
        this.localToken = localToken;
        this.users = users;
    }

    /**
     * Requests carrying a local bearer token (D-17), for which CSRF protection is not applicable.
     *
     * <p>CSRF defends against a cookie being attached automatically to a request the user never
     * meant to make. A bearer token is attached automatically by nothing, and a cross-site page
     * cannot add an {@code Authorization} header without triggering a CORS preflight this API does
     * not answer — so there is nothing here to forge. Matching on the header's presence rather than
     * its correctness is therefore safe: a request bearing a wrong token is simply unauthenticated,
     * and exempting it from CSRF grants nobody anything.
     */
    private boolean carriesLocalToken(HttpServletRequest request) {
        if (!localToken.enabled()) {
            return false;
        }
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ");
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, WebAuthnRelyingPartyOperations relyingParty,
                                    UserDetailsService userDetailsService,
                                    ApplicationEventPublisher events) throws Exception {
        var entryPoint = new ApiAuthenticationEntryPoint();
        // The passkey login filter gets an authentication manager that keeps the password factor
        // when the passkey factor arrives. Spring's WebAuthnConfigurer builds the filter with a
        // private ProviderManager and offers no hook for it, but it does post-process the filter —
        // so the manager is replaced with the same provider, wrapped. See
        // FactorMergingAuthenticationManager for what went wrong without it.
        var mergeFactors = new ObjectPostProcessor<WebAuthnAuthenticationFilter>() {
            @Override
            public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
                var provider = new WebAuthnAuthenticationProvider(relyingParty, userDetailsService);
                var manager = new ProviderManager(provider);
                manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(events));
                filter.setAuthenticationManager(new FactorMergingAuthenticationManager(manager));
                return filter;
            }
        };
        http
            .addFilterBefore(new LocalTokenAuthenticationFilter(localToken, users),
                AuthorizationFilter.class)
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(this::carriesLocalToken)
                // Readable by JavaScript on purpose: Angular's HttpClient copies this cookie into
                // the X-XSRF-TOKEN header. HttpOnly here would break that and silently disable the
                // protection rather than enforce it.
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf").permitAll()
                // Self-closing: SetupController refuses once any user exists (409), so this is
                // open only on a genuinely empty database.
                .requestMatchers("/api/v1/setup").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                // Presenting a passkey must work before the session is fully authorized — that is
                // the entire point of a second factor. These two are the assertion ceremony.
                .requestMatchers("/webauthn/authenticate/options", "/login/webauthn").permitAll()
                // Registration (/webauthn/register, /webauthn/register/options) is deliberately
                // NOT listed here. Spring Security's WebAuthn filters run ahead of the
                // authorization filter and answer those paths themselves, refusing an anonymous
                // caller with 400 before any matcher is consulted — so a rule here would be dead
                // config that reads as protection. PasskeyRegistrationTest pins the behaviour
                // instead: anonymous is refused, an authenticated session is served.
                .anyRequest().authenticated())
            // NOTE: formLogin is deliberately absent. It installs a login-page URL, and Spring's
            // MFA support then answers a missing second factor with 302 to
            // /login?factor.type=webauthn&factor.reason=missing. A redirect is unusable from an SPA
            // — fetch follows it and hands back HTML with status 200. AuthController owns login
            // instead, so every outcome is a status code (see ApiAuthenticationEntryPoint).
            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .logoutSuccessHandler((req, res, authentication) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                .deleteCookies("SESSION")
                .invalidateHttpSession(true))
            .webAuthn(webAuthn -> webAuthn
                // rpId, rpName and allowedOrigins live on the relyingPartyOperations bean below,
                // which the configurer picks up in preference to values set here; setting them
                // here too would be two places to get one thing wrong.
                // The SPA owns registration UI; Spring's built-in page would be a second,
                // unstyled surface on the same endpoints.
                .disableDefaultRegistrationPage(true)
                .withObjectPostProcessor(mergeFactors))
            .exceptionHandling(ex -> ex
                // Set for ANY request, not just as the default. The webAuthn configurer installs a
                // login-page entry point of its own, and without an explicit any-request mapping
                // that one wins for the missing-second-factor case and answers 302 to
                // /login?factor.type=webauthn — an HTML redirect an SPA cannot act on.
                .defaultAuthenticationEntryPointFor(entryPoint, AnyRequestMatcher.INSTANCE)
                .authenticationEntryPoint(entryPoint)
                // A missing factor arrives as an authorization denial and needs the same JSON
                // treatment — but a rejected CSRF token is a genuine 403 and must stay one.
                // ApiAccessDeniedHandler tells them apart.
                .accessDeniedHandler(new ApiAccessDeniedHandler()));

        return http.build();
    }

    /**
     * Built explicitly because there is no {@code formLogin} to derive one from. The event publisher
     * matters: {@link LoginAttemptListener} relies on authentication events to record attempts, and
     * a bare ProviderManager publishes none, which would silently disable lockout.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder,
                                                ApplicationEventPublisher events) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        var manager = new ProviderManager(provider);
        manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(events));
        return manager;
    }

    /** Where the authenticated context is stored between requests: the session. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(),
            new HttpSessionSecurityContextRepository());
    }

    /**
     * Delegating encoder: hashes with the current best default and can still verify older formats,
     * so the algorithm can be upgraded without locking anyone out. docs/SECURITY.md requires modern
     * hashing; the legacy app's jjwt-era approach is not carried forward.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Spring Security's own JDBC-backed passkey storage, against the tables created in V3.
     *
     * <p>Using the framework's implementations rather than hand-rolling is deliberate: credential
     * persistence is a security-critical path where a subtle mapping bug breaks authentication in
     * ways that are hard to see. The cost is two tables that do not follow this project's naming
     * conventions, which V3 documents.
     */
    @Bean
    PublicKeyCredentialUserEntityRepository userEntityRepository(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    UserCredentialRepository userCredentialRepository(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    /**
     * The relying party: who this site claims to be to an authenticator.
     *
     * <p>A bean rather than DSL settings because the passkey login filter needs the same object
     * (see the post-processor in {@link #filterChain}). rpId must equal the browser's registrable
     * domain or the authenticator refuses to sign, and allowedOrigins must list full origins; both
     * differ between local dev, the e2e stack and the deployed host, so neither is a constant.
     */
    @Bean
    WebAuthnRelyingPartyOperations relyingPartyOperations(
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository credentials) {
        var relyingParty = PublicKeyCredentialRpEntity.builder()
            .id(properties.rpId())
            .name(properties.rpName())
            .build();
        return new Webauthn4JRelyingPartyOperations(userEntities, credentials, relyingParty,
            Set.copyOf(properties.allowedOrigins()));
    }
}
