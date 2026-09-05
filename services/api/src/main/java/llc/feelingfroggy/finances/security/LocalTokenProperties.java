package llc.feelingfroggy.finances.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A bearer token that lets local automation act as the account owner (D-17).
 *
 * <p>This exists so the MCP server — and therefore Claude Code — can drive the API without a
 * browser session. It is a genuine authentication bypass and is treated as one:
 *
 * <ul>
 *   <li><b>Off unless configured.</b> An unset or blank token disables the filter entirely, so the
 *       bypass does not exist by default and cannot be switched on by accident.
 *   <li><b>Loopback only.</b> Rejected outright unless the connection came from this machine, so a
 *       leaked token is useless to anything that cannot already run code here.
 *   <li><b>Minimum length.</b> A short token is refused at startup rather than quietly accepted —
 *       a guessable bypass is worse than none, because it looks like protection.
 * </ul>
 *
 * <p>It also satisfies the passkey second factor (D-12), which it must in order to be useful and
 * which is the sharpest edge here: registering a passkey no longer covers a caller holding this
 * token. That trade is acceptable only while the token is loopback-scoped and the deployment is
 * VPN-only (D-16). See docs/SECURITY.md before widening either.
 *
 * @param value       the shared secret, from LOCAL_API_TOKEN; blank disables local-token auth
 * @param loopbackOnly refuse the token on non-loopback connections; leave true
 */
@ConfigurationProperties(prefix = "finances.local-token")
public record LocalTokenProperties(String value, boolean loopbackOnly) {

    /** Short enough to brute-force is not a token. 32 chars of base64 is ~192 bits. */
    public static final int MINIMUM_LENGTH = 32;

    public LocalTokenProperties {
        value = value == null ? "" : value.trim();
        if (!value.isEmpty() && value.length() < MINIMUM_LENGTH) {
            throw new IllegalStateException(
                "finances.local-token.value must be at least " + MINIMUM_LENGTH
                    + " characters, or blank to disable local-token auth. Generate one with: "
                    + "openssl rand -base64 36");
        }
    }

    public boolean enabled() {
        return !value.isEmpty();
    }

    /**
     * Logged once at startup when the token is on and the in-process loopback check is off.
     *
     * <p>That combination is legitimate in Docker, where the check cannot work, and it is exactly
     * the combination that makes the published port the entire boundary. The API cannot see how
     * the port is published, so it cannot refuse; what it can do is make sure nobody reads the
     * log later and says nothing warned them.
     */
    @jakarta.annotation.PostConstruct
    void announce() {
        if (enabled() && !loopbackOnly) {
            org.slf4j.LoggerFactory.getLogger(LocalTokenProperties.class).warn("""
                LOCAL_API_TOKEN is set and LOCAL_API_TOKEN_LOOPBACK_ONLY is false. The token \
                authenticates as the owner and satisfies the passkey factor, and nothing in this \
                process limits where it is accepted from: whatever address the API port is \
                published on is exactly how far the token reaches. Keep BIND_ADDR on loopback or \
                the Tailscale interface, never 0.0.0.0. See docs/SECURITY.md.""");
        }
    }
}
