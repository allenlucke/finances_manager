package llc.feelingfroggy.finances.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * What {@code POST /parse/positions} returns.
 *
 * @param asOf the snapshot date, read from the export's own "Date downloaded" line. The only
 *     as-of a positions file carries, and load-bearing: without it successive snapshots collapse
 *     into one and a position's history disappears.
 */
public record PositionsResult(
    @JsonProperty("source_format") String sourceFormat,
    @JsonProperty("as_of") LocalDate asOf,
    List<ParsedPosition> positions,
    List<String> warnings) {
}
