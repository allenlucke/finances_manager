package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

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
     * The file's own row type says refund or adjustment. Distinct from a transfer on purpose:
     * a transfer is <em>not spending</em> and stays uncategorizable, while a refund is
     * <em>negative spending</em> and must be bookable against the category it refunds. Folding the
     * two into one flag forced every refund into {@code is_transfer}, where the CHECK constraint
     * made it uncategorizable forever and the category stayed overcharged.
     */
    @JsonProperty("is_probable_refund") boolean isProbableRefund,
    /**
     * Set only for exports covering several accounts. Null for a single-account statement, where
     * the caller nominates the account instead.
     */
    @JsonProperty("account_mask") String accountMask,
    @JsonProperty("account_key") String accountKey,
    /**
     * The key as the parser computed it before 2026-09-12 — an unkeyed hash of the account
     * number, which turned out to be the number under a fifth of a second of work. Carried for
     * one release so an account linked under it is found and re-keyed on its next import; never
     * stored again. Remove with the re-linking branch in {@code ImportService.resolveAccount}.
     */
    @JsonProperty("legacy_account_key") String legacyAccountKey,
    /** The institution's own name for the account, e.g. "Cashback Free Checking". */
    @JsonProperty("account_name") String accountName,
    /**
     * The file's own word for the row — Chase's "Sale"/"Payment"/"Return", OFX's "XFER" — which
     * is what the transfer and refund hints were read from. This replaced {@code raw}, a verbatim
     * echo of every column that nothing here read and that carried whatever an issuer put in a
     * row, a cardholder's address included.
     */
    @JsonProperty("source_type") String sourceType) {

    /** The pre-re-keying shape: no legacy key, no source type. What a test builds when links are not the point. */
    public ParsedTransaction(LocalDate transactionDate, LocalDate postedDate, String description,
                             String merchant, BigDecimal amount, String direction, String externalId,
                             String dedupeKey, boolean isProbableTransfer, boolean isProbableRefund,
                             String accountMask, String accountKey, String accountName) {
        this(transactionDate, postedDate, description, merchant, amount, direction, externalId,
            dedupeKey, isProbableTransfer, isProbableRefund, accountMask, accountKey, null,
            accountName, null);
    }

    /**
     * A row with no refund signal — the shape every parser produced before the transfer/refund
     * split, and what a test builds when refunds are not what it is about.
     */
    public ParsedTransaction(LocalDate transactionDate, LocalDate postedDate, String description,
                             String merchant, BigDecimal amount, String direction, String externalId,
                             String dedupeKey, boolean isProbableTransfer, String accountMask,
                             String accountKey, String accountName) {
        this(transactionDate, postedDate, description, merchant, amount, direction, externalId,
            dedupeKey, isProbableTransfer, false, accountMask, accountKey, null, accountName, null);
    }

    /** True when this row names its own account rather than relying on a nominated one. */
    public boolean carriesAccount() {
        return accountKey != null && !accountKey.isBlank();
    }
}
