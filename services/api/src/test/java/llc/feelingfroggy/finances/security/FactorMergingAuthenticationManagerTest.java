package llc.feelingfroggy.finances.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

/** The second factor must add to the first, not replace it. See the class under test. */
@DisplayName("Merging factors on second-factor sign-in")
class FactorMergingAuthenticationManagerTest {

    private static final String EMAIL = "owner@finances.invalid";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Authentication passwordSession(String name) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null, List.of(
            new SimpleGrantedAuthority("ROLE_USER"),
            FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));
    }

    /** What WebAuthnAuthenticationProvider returns: the user's roles plus the WebAuthn factor. */
    private static WebAuthnAuthentication passkeyResult(String name) {
        var user = ImmutablePublicKeyCredentialUserEntity.builder()
            .id(Bytes.random()).name(name).displayName("Owner").build();
        return new WebAuthnAuthentication(user, List.of(
            new SimpleGrantedAuthority("ROLE_USER"),
            FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY)));
    }

    private static List<String> names(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static AuthenticationManager returning(Authentication result) {
        return request -> result;
    }

    @Test
    @DisplayName("a passkey presented on a password session yields both factors")
    void secondFactorAccumulates() {
        SecurityContextHolder.getContext().setAuthentication(passwordSession(EMAIL));
        var manager = new FactorMergingAuthenticationManager(returning(passkeyResult(EMAIL)));

        Authentication merged = manager.authenticate(passkeyResult(EMAIL));

        assertThat(merged).isInstanceOf(WebAuthnAuthentication.class);
        assertThat(merged.isAuthenticated()).isTrue();
        assertThat(merged.getName()).isEqualTo(EMAIL);
        assertThat(names(merged)).containsExactlyInAnyOrder("ROLE_USER",
            FactorGrantedAuthority.PASSWORD_AUTHORITY, FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
    }

    @Test
    @DisplayName("a passkey presented cold is a single-factor sign-in and inherits nothing")
    void noPriorAuthenticationMeansNoMerge() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        var manager = new FactorMergingAuthenticationManager(returning(passkeyResult(EMAIL)));

        Authentication result = manager.authenticate(passkeyResult(EMAIL));

        assertThat(names(result)).containsExactlyInAnyOrder("ROLE_USER",
            FactorGrantedAuthority.WEBAUTHN_AUTHORITY);
    }

    @Test
    @DisplayName("a passkey for a different user does not inherit the session's factors")
    void differentUserDoesNotMerge() {
        SecurityContextHolder.getContext().setAuthentication(passwordSession("someone@else.invalid"));
        var manager = new FactorMergingAuthenticationManager(returning(passkeyResult(EMAIL)));

        Authentication result = manager.authenticate(passkeyResult(EMAIL));

        assertThat(names(result)).doesNotContain(FactorGrantedAuthority.PASSWORD_AUTHORITY);
    }

    @Test
    @DisplayName("the email comparison is case-insensitive, like the account itself")
    void nameComparisonIgnoresCase() {
        SecurityContextHolder.getContext().setAuthentication(passwordSession(EMAIL.toUpperCase()));
        var manager = new FactorMergingAuthenticationManager(returning(passkeyResult(EMAIL)));

        assertThat(names(manager.authenticate(passkeyResult(EMAIL))))
            .contains(FactorGrantedAuthority.PASSWORD_AUTHORITY);
    }
}
