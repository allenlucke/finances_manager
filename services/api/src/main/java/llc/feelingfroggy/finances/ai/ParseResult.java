package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the parser made of an uploaded file.
 *
 * <p>{@code warnings} carries per-row failures rather than aborting the import. A statement with one
 * unparseable line should land the other several hundred rows and tell the user about the one —
 * refusing the whole file over a single malformed date would make real exports unusable.
 */
public record ParseResult(
    @JsonProperty("source_format") String sourceFormat,
    List<ParsedTransaction> transactions,
    List<String> warnings,
    /** The checkpoint for the account the caller nominated. Null for a multi-account file. */
    StatementSummary statement,
    /**
     * One checkpoint per account, for a file that holds several statements — a multi-account
     * OFX. Each names its account by the same key its rows carry, so the importer records it
     * against the account it resolved the rows to. Empty for a single-account file.
     */
    List<StatementSummary> statements) {

    /** The pre-`statements` shape: one summary, for the nominated account. */
    public ParseResult(String sourceFormat, List<ParsedTransaction> transactions,
                       List<String> warnings, StatementSummary statement) {
        this(sourceFormat, transactions, warnings, statement, List.of());
    }

    /** Never null, whatever the parser sent. */
    public List<StatementSummary> perAccountStatements() {
        return statements == null ? List.of() : statements;
    }

    /** Reconciliation metadata, when the file carries it. CSV exports usually do not. */
    public record StatementSummary(
        @JsonProperty("period_start") LocalDate periodStart,
        @JsonProperty("period_end") LocalDate periodEnd,
        /** The balance the period started from, when the file lets the parser derive it. */
        @JsonProperty("opening_balance") BigDecimal openingBalance,
        @JsonProperty("closing_balance") BigDecimal closingBalance,
        /** Set only on a per-account summary from a multi-statement file. */
        @JsonProperty("account_key") String accountKey,
        @JsonProperty("account_mask") String accountMask) {

        /** The pre-opening-balance shape, for tests and older captured fixtures. */
        public StatementSummary(LocalDate periodStart, LocalDate periodEnd, BigDecimal closingBalance) {
            this(periodStart, periodEnd, null, closingBalance, null, null);
        }

        /** The single-account shape: no account key. */
        public StatementSummary(LocalDate periodStart, LocalDate periodEnd,
                                BigDecimal openingBalance, BigDecimal closingBalance) {
            this(periodStart, periodEnd, openingBalance, closingBalance, null, null);
        }
    }
}
