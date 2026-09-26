package llc.feelingfroggy.finances.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Recurring charges, found in the ledger rather than declared (M9, D-21).
 *
 * <p>Arithmetic, not a model: rows are grouped by a normalized merchant and direction, and a group
 * with three or more occurrences at a steady interval is a series. Every series carries its
 * evidence — how many times, the typical amount, the last date — and a status a person can act
 * on: upcoming, on track, or missing. Nothing here writes anything; it is a way of reading.
 */
@Service
public class RecurringService {

    /** One recurring thing, with its evidence. */
    public record Series(String key, String label, String accountName, String direction, String cadence,
                         BigDecimal typicalAmount, boolean amountVaries, BigDecimal lastAmount,
                         LocalDate lastDate, LocalDate nextExpected, int occurrences, String status) {
    }

    /** An expected occurrence inside the forecast window. */
    public record Expected(LocalDate date, String label, String accountName, String direction, BigDecimal amount) {
    }

    /** Something that looks wrong: a duplicate, or an amount far from usual. */
    public record Anomaly(String kind, String severity, String text, LocalDate date) {
    }

    /** A series the person said is not recurring, kept so it can be unsaid. */
    public record Muted(String key, String label) {
    }

    /**
     * @param monthlyRecurringOut what the found series add up to per month at their cadence,
     *     missing ones excluded — a rate, independent of the window
     * @param liquidCash the positive balances of checking, savings and cash accounts
     * @param runwayWeeks how many weeks that cash covers the monthly rate; null without both
     */
    public record Report(List<Series> series, List<Expected> upcoming, List<Anomaly> anomalies,
                         BigDecimal expectedOut, BigDecimal expectedIn, int days,
                         BigDecimal monthlyRecurringOut, BigDecimal liquidCash, BigDecimal runwayWeeks,
                         List<Muted> muted) {
    }

    private record Row(long id, long accountId, String accountName, String description, String merchant,
                       String direction, BigDecimal amount, LocalDate date) {
    }

    private static final Pattern NOISE = Pattern.compile("[^A-Z ]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RecurringService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The merchant, with digits, punctuation and trailing location noise stripped: the grouping key. */
    public static String merchantKey(String merchant, String description) {
        String source = merchant != null && !merchant.isBlank() ? merchant : description == null ? "" : description;
        String cleaned = SPACES.matcher(NOISE.matcher(source.toUpperCase(Locale.ROOT)).replaceAll(" ")).replaceAll(" ").strip();
        // Letters left over from a reference like "MKTPL*2K3J4" are not words; drop the one-letter bits.
        var words = java.util.Arrays.stream(cleaned.split(" ")).filter(w -> w.length() > 1).toList();
        if (words.isEmpty()) {
            return cleaned.isBlank() ? "" : cleaned.split(" ")[0];
        }
        return String.join(" ", words.subList(0, Math.min(words.size(), 3)));
    }

    public Report report(Long userId, int days) {
        LocalDate today = LocalDate.now(clock);
        List<Row> rows = jdbc.query("""
            SELECT r.id, r.account_id, a.name AS account_name, r.description, r.merchant, r.direction,
                   r.amount, r.transaction_date
            FROM v_transaction_resolved r JOIN account a ON a.id = r.account_id
            WHERE r.user_id = ? AND r.transaction_date >= ? AND r.is_transfer = false AND r.pending = false
            ORDER BY r.transaction_date, r.id
            """,
            (rs, i) -> new Row(rs.getLong("id"), rs.getLong("account_id"), rs.getString("account_name"),
                rs.getString("description"), rs.getString("merchant"), rs.getString("direction"),
                rs.getBigDecimal("amount"), rs.getObject("transaction_date", LocalDate.class)),
            userId, today.minusDays(400));

        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row row : rows) {
            String key = merchantKey(row.merchant(), row.description());
            if (key.isBlank()) {
                continue;
            }
            groups.computeIfAbsent(row.accountId() + "|" + row.direction() + "|" + key, k -> new ArrayList<>()).add(row);
        }

        Map<String, String> muted = new LinkedHashMap<>();
        jdbc.query("SELECT series_key, label FROM recurring_mute WHERE user_id = ? ORDER BY id",
            rs -> { muted.put(rs.getString("series_key"), rs.getString("label")); }, userId);

        var series = new ArrayList<Series>();
        var anomalies = new ArrayList<Anomaly>();
        for (var entry : groups.entrySet()) {
            List<Row> group = entry.getValue();
            if (muted.containsKey(entry.getKey())) {
                continue;
            }
            anomalies.addAll(duplicatesIn(group));
            if (group.size() < 3) {
                continue;
            }
            Series s = seriesOf(entry.getKey(), group, today);
            if (s != null) {
                series.add(s);
                anomalies.addAll(unusualAmount(group, s));
            }
        }
        series.sort(Comparator.comparing(Series::nextExpected, Comparator.nullsLast(Comparator.naturalOrder())));

        var upcoming = new ArrayList<Expected>();
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal in = BigDecimal.ZERO;
        LocalDate horizon = today.plusDays(days);
        for (Series s : series) {
            if (s.nextExpected() == null || "missing".equals(s.status())) {
                continue;
            }
            LocalDate at = s.nextExpected().isBefore(today) ? today : s.nextExpected();
            while (!at.isAfter(horizon)) {
                upcoming.add(new Expected(at, s.label(), s.accountName(), s.direction(), s.typicalAmount()));
                if ("debit".equals(s.direction())) {
                    out = out.add(s.typicalAmount());
                } else {
                    in = in.add(s.typicalAmount());
                }
                LocalDate next = advance(at, s.cadence());
                if (!next.isAfter(at)) {
                    break;
                }
                at = next;
            }
        }
        upcoming.sort(Comparator.comparing(Expected::date));
        anomalies.sort(Comparator.comparing(Anomaly::date).reversed());

        // The rate, not the window: what the found outflows add up to per month at their cadence.
        BigDecimal monthlyOut = BigDecimal.ZERO;
        for (Series s : series) {
            if ("debit".equals(s.direction()) && !"missing".equals(s.status())) {
                monthlyOut = monthlyOut.add(s.typicalAmount().multiply(perMonth(s.cadence())));
            }
        }
        monthlyOut = monthlyOut.setScale(2, RoundingMode.HALF_UP);
        BigDecimal liquid = jdbc.queryForObject("""
            SELECT COALESCE(SUM(balance), 0) FROM v_account_balance
            WHERE user_id = ? AND account_type IN ('checking', 'savings', 'cash') AND balance > 0
            """, BigDecimal.class, userId);
        BigDecimal runway = null;
        if (liquid != null && liquid.signum() > 0 && monthlyOut.signum() > 0) {
            BigDecimal perDay = monthlyOut.divide(new BigDecimal("30.4375"), 6, RoundingMode.HALF_UP);
            runway = liquid.divide(perDay, 6, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(7), 1, RoundingMode.HALF_UP);
        }
        var mutedList = muted.entrySet().stream().map(e -> new Muted(e.getKey(), e.getValue())).toList();
        return new Report(series, upcoming, anomalies, out.setScale(2, RoundingMode.HALF_UP),
            in.setScale(2, RoundingMode.HALF_UP), days, monthlyOut,
            liquid == null ? BigDecimal.ZERO : liquid.setScale(2, RoundingMode.HALF_UP), runway, mutedList);
    }

    /** Occurrences per month for a cadence. */
    static BigDecimal perMonth(String cadence) {
        return switch (cadence) {
            case "weekly" -> new BigDecimal("52").divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
            case "biweekly" -> new BigDecimal("26").divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
            case "quarterly" -> BigDecimal.ONE.divide(BigDecimal.valueOf(3), 6, RoundingMode.HALF_UP);
            case "yearly" -> BigDecimal.ONE.divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
            default -> BigDecimal.ONE;
        };
    }

    /** The person's word: this key is (or is again) not a recurring charge. */
    @org.springframework.transaction.annotation.Transactional
    public void setMuted(Long userId, String key, String label, boolean muted) {
        if (key == null || key.isBlank()) {
            throw new llc.feelingfroggy.finances.domain.DomainRuleViolation("A series key is needed");
        }
        if (muted) {
            jdbc.update("""
                INSERT INTO recurring_mute (user_id, series_key, label) VALUES (?, ?, ?)
                ON CONFLICT (user_id, series_key) DO UPDATE SET label = COALESCE(EXCLUDED.label, recurring_mute.label)
                """, userId, key, label);
        } else {
            jdbc.update("DELETE FROM recurring_mute WHERE user_id = ? AND series_key = ?", userId, key);
        }
    }

    private Series seriesOf(String key, List<Row> group, LocalDate today) {
        var dates = group.stream().map(Row::date).distinct().sorted().toList();
        if (dates.size() < 3) {
            return null;
        }
        var intervals = new ArrayList<Long>();
        for (int i = 1; i < dates.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
        }
        long median = medianLong(intervals);
        String cadence = cadenceOf(median);
        if (cadence == null) {
            return null;
        }
        long tolerance = Math.max(2, Math.round(median * 0.2));
        long steady = intervals.stream().filter(d -> Math.abs(d - median) <= tolerance).count();
        if (steady * 100 < intervals.size() * 60L) {
            return null;
        }
        var amounts = group.stream().map(Row::amount).sorted().toList();
        BigDecimal typical = medianDecimal(amounts).setScale(2, RoundingMode.HALF_UP);
        boolean varies = amounts.stream().anyMatch(a -> deviates(a, typical, new BigDecimal("0.25")));
        Row last = group.get(group.size() - 1);
        LocalDate next = advance(last.date(), cadence);
        long grace = switch (cadence) {
            case "weekly", "biweekly" -> 3;
            case "monthly" -> 5;
            default -> 10;
        };
        String status = next.isBefore(today.minusDays(grace)) ? "missing"
            : !next.isAfter(today.plusDays(14)) ? "upcoming" : "on track";
        String label = mostCommon(group.stream().map(r -> r.merchant() != null && !r.merchant().isBlank()
            ? r.merchant() : r.description()).toList());
        return new Series(key, label, last.accountName(), last.direction(), cadence, typical, varies,
            last.amount().setScale(2, RoundingMode.HALF_UP), last.date(), next, dates.size(), status);
    }

    private List<Anomaly> duplicatesIn(List<Row> group) {
        var found = new ArrayList<Anomaly>();
        for (int i = 1; i < group.size(); i++) {
            Row a = group.get(i - 1);
            Row b = group.get(i);
            if (a.amount().compareTo(b.amount()) == 0 && ChronoUnit.DAYS.between(a.date(), b.date()) <= 2
                && a.id() != b.id()) {
                found.add(new Anomaly("duplicate", "high", "Possible duplicate: " + label(b) + " for "
                    + dollars(b.amount()) + " on " + a.date() + " and again on " + b.date(), b.date()));
            }
        }
        return found;
    }

    private List<Anomaly> unusualAmount(List<Row> group, Series s) {
        Row last = group.get(group.size() - 1);
        BigDecimal typical = s.typicalAmount();
        if (typical.signum() == 0) {
            return List.of();
        }
        BigDecimal ratio = last.amount().divide(typical, 4, RoundingMode.HALF_UP);
        boolean big = ratio.compareTo(new BigDecimal("1.5")) >= 0
            && last.amount().subtract(typical).compareTo(new BigDecimal("5")) > 0;
        if (!big) {
            return List.of();
        }
        return List.of(new Anomaly("unusual_amount", "medium", label(last) + " was " + dollars(last.amount())
            + " on " + last.date() + ", usually about " + dollars(typical), last.date()));
    }

    public static String cadenceOf(long medianDays) {
        if (medianDays >= 6 && medianDays <= 8) return "weekly";
        if (medianDays >= 13 && medianDays <= 15) return "biweekly";
        if (medianDays >= 27 && medianDays <= 33) return "monthly";
        if (medianDays >= 84 && medianDays <= 96) return "quarterly";
        if (medianDays >= 355 && medianDays <= 375) return "yearly";
        return null;
    }

    static LocalDate advance(LocalDate from, String cadence) {
        return switch (cadence) {
            case "weekly" -> from.plusWeeks(1);
            case "biweekly" -> from.plusWeeks(2);
            case "monthly" -> from.plusMonths(1);
            case "quarterly" -> from.plusMonths(3);
            case "yearly" -> from.plusYears(1);
            default -> from;
        };
    }

    private static boolean deviates(BigDecimal amount, BigDecimal typical, BigDecimal fraction) {
        if (typical.signum() == 0) {
            return amount.signum() != 0;
        }
        return amount.subtract(typical).abs().divide(typical, 4, RoundingMode.HALF_UP).compareTo(fraction) > 0;
    }

    private static long medianLong(List<Long> values) {
        var sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    private static BigDecimal medianDecimal(List<BigDecimal> sorted) {
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2)
            : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private static String mostCommon(List<String> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String v : values) {
            counts.merge(v.strip(), 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private static String label(Row row) {
        return row.merchant() != null && !row.merchant().isBlank() ? row.merchant() : row.description();
    }

    private static String dollars(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static BigDecimal cents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).max(CENT.negate());
    }
}
