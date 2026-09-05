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
    StatementSummary statement) {

    /** Reconciliation metadata, when the file carries it. CSV exports usually do not. */
    public record StatementSummary(
        @JsonProperty("period_start") LocalDate periodStart,
        @JsonProperty("period_end") LocalDate periodEnd,
        /** The balance the period started from, when the file lets the parser derive it. */
        @JsonProperty("opening_balance") BigDecimal openingBalance,
        @JsonProperty("closing_balance") BigDecimal closingBalance) {

        /** The pre-opening-balance shape, for tests and older captured fixtures. */
        public StatementSummary(LocalDate periodStart, LocalDate periodEnd, BigDecimal closingBalance) {
            this(periodStart, periodEnd, null, closingBalance);
        }
    }
}
