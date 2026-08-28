package llc.feelingfroggy.finances.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebAuthn relying-party settings (D-12).
 *
 * <p>{@code rpId} must be the site's registrable domain and <strong>must match the origin the
 * browser is on</strong>, or the authenticator refuses to sign. It is configuration rather than a
 * constant because it necessarily differs between local development and the deployed host.
 *
 * @param rpId          relying-party id: the bare domain, no scheme, no port
 * @param rpName        human-readable name shown in the browser's passkey prompt
 * @param allowedOrigins full origins (scheme, host, port) permitted to perform WebAuthn ceremonies
 */
@ConfigurationProperties(prefix = "finances.security.webauthn")
public record SecurityProperties(String rpId, String rpName, List<String> allowedOrigins) {

    public SecurityProperties {
        if (rpId == null || rpId.isBlank()) {
            rpId = "localhost";
        }
        if (rpName == null || rpName.isBlank()) {
            rpName = "Finances Manager";
        }
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:4200", "http://localhost:8080");
        }
    }
}
