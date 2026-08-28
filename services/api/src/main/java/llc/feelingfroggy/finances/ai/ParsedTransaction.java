package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One normalized row as the Python parser produced it.
 *
 * <p>This mirrors {@code finances_ai.models.ParsedTransaction} field for field. The two are one
 * wire contract: changing either without the other breaks import silently, because JSON will
 * happily leave an unmapped field null rather than fail.
 *
 * <p>Snake_case names come straight off the wire, so the mapping is visible rather than relying on
 * a global naming strategy that a future config change could flip.
 *
 * @param amount always a positive magnitude; {@code direction} carries the sign
 * @param isProbableTransfer the source file's own row type says payment/refund/adjustment. A hint
 *     from the institution, not a decision — this service decides what it means.
 */
public record ParsedTransaction(
    @JsonProperty("transaction_date") LocalDate transactionDate,
    @JsonProperty("posted_date") LocalDate postedDate,
    String description,
    String merchant,
    BigDecimal amount,
    String direction,
    @JsonProperty("external_id") String externalId,
    @JsonProperty("dedupe_key") String dedupeKey,
    @JsonProperty("is_probable_transfer") boolean isProbableTransfer,
    /**
     * Set only for exports covering several accounts. Null for a single-account statement, where
     * the caller nominates the account instead.
     */
    @JsonProperty("account_mask") String accountMask,
    @JsonProperty("account_key") String accountKey,
    /** The institution's own name for the account, e.g. "Cashback Free Checking". */
    @JsonProperty("account_name") String accountName,
    Map<String, String> raw) {

    /** True when this row names its own account rather than relying on a nominated one. */
    public boolean carriesAccount() {
        return accountKey != null && !accountKey.isBlank();
    }
}
