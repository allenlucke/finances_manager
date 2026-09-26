package llc.feelingfroggy.finances.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The daily digest (M8).
 *
 * @param scheduled whether the API pushes the digest on its own, once a day at each person's
 *     chosen time. Off in tests; compose turns it on. The dashboard's "needs a look" card is
 *     composed on request regardless.
 */
@ConfigurationProperties(prefix = "finances.digest")
public record DigestProperties(boolean scheduled) {
}
