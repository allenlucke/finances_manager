package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * One holding from a brokerage positions export, as the Python parser produced it.
 *
 * <p>Mirrors {@code finances_ai.models.ParsedPosition} field for field — one wire contract, and
 * changing either side alone breaks import silently, because JSON leaves an unmapped field null
 * rather than failing.
 *
 * <p>A position is a <strong>snapshot</strong>, not a money movement. None of the ledger's
 * debit/credit sign convention applies here: {@code currentValue} is a magnitude with no direction.
 *
 * @param quantity null for cash and money-market rows, which have a value but no share count
 * @param costBasisTotal null where the export writes {@code --}, which means not-applicable and is
 *     not the same as zero
 * @param isCash decided by symbol. Never by the export's {@code Type} column — that is the
 *     account's registration (Cash or Margin), and reading it as "is this cash" classified AAPL as
 *     a cash holding.
 */
public record ParsedPosition(
    /** Last four of the account number. The parser never returns the full value. */
    @JsonProperty("account_mask") String accountMask,
    /** Stable keyed id from the full number, so re-imports match without anyone handling it. */
    @JsonProperty("account_key") String accountKey,
    /** The pre-2026-09-12 unkeyed id, for re-linking only. See ParsedTransaction. */
    @JsonProperty("legacy_account_key") String legacyAccountKey,
    @JsonProperty("account_name") String accountName,
    String symbol,
    String description,
    BigDecimal quantity,
    @JsonProperty("last_price") BigDecimal lastPrice,
    @JsonProperty("current_value") BigDecimal currentValue,
    @JsonProperty("cost_basis_total") BigDecimal costBasisTotal,
    @JsonProperty("average_cost_basis") BigDecimal averageCostBasis,
    @JsonProperty("total_gain_loss") BigDecimal totalGainLoss,
    @JsonProperty("is_cash") boolean isCash,
    @JsonProperty("account_registration") String accountRegistration) {

    /** The pre-re-keying shape: no legacy key. */
    public ParsedPosition(String accountMask, String accountKey, String accountName, String symbol,
                          String description, BigDecimal quantity, BigDecimal lastPrice,
                          BigDecimal currentValue, BigDecimal costBasisTotal,
                          BigDecimal averageCostBasis, BigDecimal totalGainLoss, boolean isCash,
                          String accountRegistration) {
        this(accountMask, accountKey, null, accountName, symbol, description, quantity, lastPrice,
            currentValue, costBasisTotal, averageCostBasis, totalGainLoss, isCash,
            accountRegistration);
    }

    /** True when this row names its own account. A positions file always does. */
    public boolean carriesAccount() {
        return accountKey != null && !accountKey.isBlank();
    }
}
