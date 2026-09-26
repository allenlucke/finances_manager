package llc.feelingfroggy.finances.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.CategorizerClient;
import llc.feelingfroggy.finances.domain.Categorization;
import llc.feelingfroggy.finances.domain.CategorizationMethod;
import llc.feelingfroggy.finances.domain.Category;
import llc.feelingfroggy.finances.domain.Resolution;
import llc.feelingfroggy.finances.domain.Transaction;
import llc.feelingfroggy.finances.repo.CategorizationRepository;
import llc.feelingfroggy.finances.repo.CategoryRepository;
import llc.feelingfroggy.finances.repo.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automatic categorization, the first two tiers of D-15 (M3a, D-22).
 *
 * <p>Similarity first: the person's own history, grouped by the same merchant key the recurring
 * detector uses — earlier rows from this merchant that were categorized, and how consistently.
 * Then the rules tier in the Python service. The higher confidence wins. A suggestion at or above
 * {@link #APPLY_AT} with a category the person has is applied without waiting; anything lower, or
 * naming a category they do not have yet, waits in the review queue with its reason. Every
 * decision a person makes on a row resolves the suggestion — accepted, corrected, rejected — and
 * that record is what the accuracy figure is measured from and what the next tier will learn from.
 *
 * <p>A suggestion of nothing ({@code method="none"}) is never a row. A suggestion that says
 * "transfer" is ignored here: only the file's own word marks a transfer (CLAUDE.md).
 */
@Service
public class CategorizationService {

    private static final Logger log = LoggerFactory.getLogger(CategorizationService.class);
    static final BigDecimal APPLY_AT = new BigDecimal("0.90");

    public record Outcome(int considered, int suggested, int applied, List<String> notes) {
    }

    public record Stats(long open, long applied, long accepted, long corrected, long rejected,
                        long byRule, long bySimilarity, BigDecimal precisionPct) {
    }

    private record Candidate(Category category, String name, BigDecimal confidence,
                             CategorizationMethod method, String rationale) {
    }

    private final CategorizationRepository categorizations;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final CategorizerClient categorizer;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CategorizationService(CategorizationRepository categorizations, CategoryRepository categories,
                                 TransactionRepository transactions, CategorizerClient categorizer,
                                 JdbcTemplate jdbc, Clock clock) {
        this.categorizations = categorizations;
        this.categories = categories;
        this.transactions = transactions;
        this.categorizer = categorizer;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Suggests for the given rows; applies the confident ones. Rows already decided are left alone. */
    @Transactional
    public Outcome suggestFor(Long userId, List<Transaction> candidates) {
        List<Transaction> rows = candidates.stream()
            .filter(t -> !t.isTransfer() && t.getCategory() == null && t.getDeletedAt() == null)
            .toList();
        var notes = new ArrayList<String>();
        if (rows.isEmpty()) {
            return new Outcome(0, 0, 0, notes);
        }
        Map<String, Category> byName = new HashMap<>();
        for (Category c : categories.findByUserIdOrderByName(userId)) {
            byName.put(c.getName().strip().toLowerCase(Locale.ROOT), c);
        }
        Map<Long, Candidate> best = new LinkedHashMap<>();

        // Tier: the person's own history.
        Map<String, Map<Long, Integer>> history = similarityIndex(userId);
        for (Transaction row : rows) {
            String key = RecurringService.merchantKey(row.getMerchant(), row.getDescription());
            Map<Long, Integer> counts = history.get(key);
            if (counts == null || key.isBlank()) {
                continue;
            }
            var top = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();
            BigDecimal share = BigDecimal.valueOf(top.getValue()).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
            if (top.getValue() < 2 || share.compareTo(new BigDecimal("0.80")) < 0) {
                continue;
            }
            Category category = categories.findByIdAndUserId(top.getKey(), userId).orElse(null);
            if (category == null) {
                continue;
            }
            BigDecimal base = top.getValue() >= 5 ? new BigDecimal("0.95")
                : top.getValue() >= 3 ? new BigDecimal("0.92") : new BigDecimal("0.86");
            BigDecimal confidence = share.multiply(base).setScale(4, RoundingMode.HALF_UP);
            best.put(row.getId(), new Candidate(category, category.getName(), confidence, CategorizationMethod.SIMILARITY,
                "Matched " + total + " earlier row" + (total == 1 ? "" : "s") + " from " + key + ", "
                    + top.getValue() + " of them " + category.getName()));
        }

        // Tier: the rules, per account type. The higher confidence wins.
        Map<String, Transaction> byKey = new HashMap<>();
        Map<String, List<Transaction>> byType = new LinkedHashMap<>();
        for (Transaction row : rows) {
            byKey.put(row.getDedupeKey(), row);
            byType.computeIfAbsent(row.getAccount().getAccountType().code(), k -> new ArrayList<>()).add(row);
        }
        for (var group : byType.entrySet()) {
            List<CategorizerClient.WireSuggestion> suggestions;
            try {
                suggestions = categorizer.categorize(group.getValue().stream().map(t -> new CategorizerClient.Row(
                    t.getTransactionDate(), t.getDescription(), t.getMerchant(),
                    t.getAmount().stripTrailingZeros().toPlainString(), t.getDirection().code(),
                    t.getDedupeKey(), t.getSourceType(), false, false)).toList(), group.getKey());
            } catch (AiServiceClient.AiServiceException e) {
                notes.add("Rule suggestions were not available: " + e.getMessage());
                continue;
            }
            for (var s : suggestions) {
                Transaction row = byKey.get(s.dedupeKey());
                if (row == null || s.category() == null || s.isTransfer() || "none".equals(s.method())
                    || !"rule".equals(s.method()) && !"model".equals(s.method())) {
                    continue;
                }
                BigDecimal confidence = BigDecimal.valueOf(s.confidence()).setScale(4, RoundingMode.HALF_UP);
                Candidate existing = best.get(row.getId());
                if (existing != null && existing.confidence().compareTo(confidence) >= 0) {
                    continue;
                }
                Category category = byName.get(s.category().strip().toLowerCase(Locale.ROOT));
                best.put(row.getId(), new Candidate(category, s.category(), confidence,
                    "model".equals(s.method()) ? CategorizationMethod.MODEL : CategorizationMethod.RULE,
                    s.rationale()));
            }
        }

        Instant now = Instant.now(clock);
        int applied = 0;
        for (Transaction row : rows) {
            Candidate c = best.get(row.getId());
            if (c == null) {
                continue;
            }
            categorizations.findOpenFor(row.getId()).ifPresent(categorizations::delete);
            var record = new Categorization(row, c.category(), c.confidence(), c.method());
            record.setUserId(userId);
            record.setRationale(c.rationale());
            record.setSuggestedName(c.name());
            if (c.category() != null && c.confidence().compareTo(APPLY_AT) >= 0) {
                row.setCategory(c.category());
                transactions.save(row);
                record.applied(now);
                applied++;
            }
            categorizations.save(record);
        }
        log.info("categorization considered={} suggested={} applied={}", rows.size(), best.size(), applied);
        return new Outcome(rows.size(), best.size(), applied, notes);
    }

    /** Everything in the review queue, up to a sensible page. */
    @Transactional
    public Outcome suggestUncategorized(Long userId) {
        return suggestFor(userId, transactions.findNeedingReview(userId, PageRequest.of(0, 2000)).getContent());
    }

    /**
     * The person decided. Resolves the newest suggestion for the row unless a person already had:
     * chose the suggested category → accepted; chose another → corrected; chose none → rejected.
     */
    @Transactional
    public void resolved(Transaction row, Category chosen) {
        var latest = categorizations.findTopByTransactionIdOrderByIdDesc(row.getId());
        if (latest.isEmpty()) {
            return;
        }
        Categorization c = latest.get();
        if (c.getResolution() != null && c.getResolution() != Resolution.APPLIED) {
            return;
        }
        Instant now = Instant.now(clock);
        if (chosen == null) {
            c.reject(now);
        } else if (c.getSuggestedCategory() != null && chosen.getId().equals(c.getSuggestedCategory().getId())) {
            c.accept(now);
        } else if (c.getSuggestedCategory() == null && c.getSuggestedName() != null
            && c.getSuggestedName().equalsIgnoreCase(chosen.getName())) {
            c.acceptAs(chosen, now);
        } else {
            c.correct(chosen, now);
        }
        categorizations.save(c);
    }

    /** How the suggestions have done, from what people decided. Measured, not felt. */
    public Stats stats(Long userId) {
        var counts = new HashMap<String, Long>();
        jdbc.query("SELECT coalesce(resolution, 'open') AS r, count(*) AS n FROM categorization WHERE user_id = ? GROUP BY 1",
            rs -> { counts.put(rs.getString("r"), rs.getLong("n")); }, userId);
        var methods = new HashMap<String, Long>();
        jdbc.query("SELECT method, count(*) AS n FROM categorization WHERE user_id = ? GROUP BY 1",
            rs -> { methods.put(rs.getString("method"), rs.getLong("n")); }, userId);
        long applied = counts.getOrDefault("applied", 0L);
        long accepted = counts.getOrDefault("accepted", 0L);
        long corrected = counts.getOrDefault("corrected", 0L);
        long judged = applied + accepted + corrected;
        BigDecimal precision = judged == 0 ? null
            : BigDecimal.valueOf(applied + accepted).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(judged), 1, RoundingMode.HALF_UP);
        return new Stats(counts.getOrDefault("open", 0L), applied, accepted, corrected,
            counts.getOrDefault("rejected", 0L), methods.getOrDefault("rule", 0L),
            methods.getOrDefault("similarity", 0L), precision);
    }

    private Map<String, Map<Long, Integer>> similarityIndex(Long userId) {
        var index = new HashMap<String, Map<Long, Integer>>();
        jdbc.query("""
            SELECT description, merchant, category_id FROM transaction
            WHERE user_id = ? AND category_id IS NOT NULL AND deleted_at IS NULL AND is_transfer = false
              AND transaction_date >= ?
            """,
            rs -> {
                String key = RecurringService.merchantKey(rs.getString("merchant"), rs.getString("description"));
                if (!key.isBlank()) {
                    index.computeIfAbsent(key, k -> new HashMap<>()).merge(rs.getLong("category_id"), 1, Integer::sum);
                }
            },
            userId, LocalDate.now(clock).minusDays(730));
        return index;
    }
}
