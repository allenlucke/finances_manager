package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.CategorizerClient;
import llc.feelingfroggy.finances.ai.CategorizerClient.WireSuggestion;
import llc.feelingfroggy.finances.ai.ParseResult;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.service.ImportService;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/**
 * Automatic categorization (M3a, D-22): the person's history first, then the rules; confident
 * suggestions applied, the rest waiting with a reason; every decision resolving its suggestion,
 * and the accuracy figure measured from that.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Automatic categorization")
class AutoCategorizationTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ImportService imports;
    @Autowired private AccountRepository accounts;
    @MockitoBean private AiServiceClient aiService;
    @MockitoBean private CategorizerClient categorizer;
    @LocalServerPort private int port;

    private ApiClient api;
    private long userId;
    private long cardId;
    private long groceries;
    private long fuel;

    @BeforeEach
    void setUp() throws IOException {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        userId = jdbc.queryForObject("SELECT id FROM app_user", Long.class);
        long entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        cardId = api.postJson("/api/v1/accounts", Map.of("ledgerEntityId", entityId, "name", "Card",
            "accountType", "credit_card")).json().get("id").asLong();
        groceries = api.postJson("/api/v1/categories", Map.of("name", "Groceries", "kind", "expense")).json().get("id").asLong();
        fuel = api.postJson("/api/v1/categories", Map.of("name", "Fuel", "kind", "expense")).json().get("id").asLong();
        when(aiService.parseCsv(any(), any(), any())).thenReturn(fixture());
    }

    private static ParseResult fixture() throws IOException {
        try (var stream = new ClassPathResource("fixtures/parse-result-chase.json").getInputStream()) {
            return new ObjectMapper().readValue(stream, ParseResult.class);
        }
    }

    /** The rules tier as the Python service would answer for the fixture's rows. */
    private void rulesAnswer() {
        when(categorizer.categorize(any(), anyString())).thenAnswer(call -> {
            List<CategorizerClient.Row> rows = call.getArgument(0);
            return rows.stream().map(r -> {
                String d = r.description().toUpperCase();
                if (d.startsWith("KROGER")) {
                    return new WireSuggestion(r.dedupeKey(), "Groceries", 0.90, "rule", "merchant rule: KROGER", false);
                }
                if (d.startsWith("SHELL")) {
                    return new WireSuggestion(r.dedupeKey(), "Fuel", 0.90, "rule", "merchant rule: SHELL", false);
                }
                if (d.startsWith("NETFLIX")) {
                    return new WireSuggestion(r.dedupeKey(), "Subscriptions", 0.90, "rule", "merchant rule: NETFLIX", false);
                }
                return new WireSuggestion(r.dedupeKey(), null, 0.0, "none", "No tier produced a candidate; needs review", false);
            }).toList();
        });
    }

    private void importFixture() {
        var card = accounts.findByIdAndUserId(cardId, userId).orElseThrow();
        imports.importStatement(userId, card, "irrelevant: the parser is mocked".getBytes(), "chase.csv");
    }

    private Long categoryOf(String descriptionPrefix) {
        return jdbc.queryForObject("SELECT category_id FROM transaction WHERE description LIKE ? ORDER BY id LIMIT 1",
            Long.class, descriptionPrefix + "%");
    }

    @Test
    @DisplayName("confident rule hits are applied, a category the person lacks waits by name, and decisions resolve them")
    void rulesApplyAndDecisionsResolve() {
        rulesAnswer();

        importFixture();

        assertThat(categoryOf("KROGER")).isEqualTo(groceries);
        assertThat(categoryOf("SHELL")).isEqualTo(fuel);
        assertThat(categoryOf("NETFLIX")).isNull();
        assertThat(categoryOf("SOME NEW MERCHANT")).isNull();
        var batch = api.get("/api/v1/imports").json().get(0);
        assertThat(batch.get("autoCategorized").asInt()).isEqualTo(3);
        assertThat(batch.get("suggested").asInt()).isEqualTo(1);
        assertThat(batch.get("warnings")).isEmpty();

        var review = api.get("/api/v1/transactions/review").json().get("content");
        assertThat(review).hasSize(2);
        var netflix = review.get(0).get("description").asText().startsWith("NETFLIX") ? review.get(0) : review.get(1);
        var other = netflix == review.get(0) ? review.get(1) : review.get(0);
        assertThat(netflix.get("suggestion").get("categoryId").isNull()).isTrue();
        assertThat(netflix.get("suggestion").get("suggestedName").asText()).isEqualTo("Subscriptions");
        assertThat(netflix.get("suggestion").get("method").asText()).isEqualTo("rule");
        assertThat(other.get("suggestion").isNull()).isTrue();

        var stats = api.get("/api/v1/transactions/categorization-stats").json();
        assertThat(stats.get("applied").asLong()).isEqualTo(3);
        assertThat(stats.get("open").asLong()).isEqualTo(1);
        assertThat(new BigDecimal(stats.get("precisionPct").asText())).isEqualByComparingTo("100.0");

        // The person creates Subscriptions and chooses it: accepted by name.
        long subscriptions = api.postJson("/api/v1/categories", Map.of("name", "Subscriptions", "kind", "expense")).json().get("id").asLong();
        api.putJson("/api/v1/transactions/" + netflix.get("id").asLong() + "/category", Map.of("categoryId", subscriptions));
        // And moves the Shell row elsewhere: corrected.
        long shellId = jdbc.queryForObject("SELECT id FROM transaction WHERE description LIKE 'SHELL%'", Long.class);
        api.putJson("/api/v1/transactions/" + shellId + "/category", Map.of("categoryId", groceries));

        stats = api.get("/api/v1/transactions/categorization-stats").json();
        assertThat(stats.get("accepted").asLong()).isEqualTo(1);
        assertThat(stats.get("corrected").asLong()).isEqualTo(1);
        assertThat(stats.get("applied").asLong()).isEqualTo(2);
        assertThat(stats.get("open").asLong()).isZero();
        assertThat(new BigDecimal(stats.get("precisionPct").asText())).isEqualByComparingTo("75.0");
        assertThat(api.get("/api/v1/transactions/review").json().get("content")).hasSize(1);
    }

    @Test
    @DisplayName("the person's own history outranks a silent rule set")
    void historyIsATier() {
        when(categorizer.categorize(any(), anyString())).thenAnswer(call -> {
            List<CategorizerClient.Row> rows = call.getArgument(0);
            return rows.stream().map(r -> new WireSuggestion(r.dedupeKey(), null, 0.0, "none", "nothing", false)).toList();
        });
        for (String date : List.of("2026-05-02", "2026-06-06", "2026-07-09")) {
            api.postJson("/api/v1/transactions", Map.of("accountId", cardId, "transactionDate", date, "amount", "61.10",
                "direction", "debit", "description", "KROGER #4521", "categoryId", groceries));
        }

        importFixture();

        assertThat(categoryOf("KROGER")).isEqualTo(groceries);
        assertThat(categoryOf("SHELL")).isNull();
        var kroger = jdbc.queryForMap("SELECT c.method, c.confidence, c.resolution, c.rationale FROM categorization c "
            + "JOIN transaction t ON t.id = c.transaction_id WHERE t.description LIKE 'KROGER%' AND t.direction = 'debit'");
        assertThat(kroger.get("method")).isEqualTo("similarity");
        assertThat(kroger.get("resolution")).isEqualTo("applied");
        assertThat((BigDecimal) kroger.get("confidence")).isEqualByComparingTo("0.9200");
        assertThat(kroger.get("rationale").toString()).contains("3 earlier rows from KROGER");

        // Asking again for the rest changes nothing: no tier has anything to say.
        var outcome = api.postJson("/api/v1/transactions/suggest", Map.of()).json();
        assertThat(outcome.get("considered").asInt()).isEqualTo(3);
        assertThat(outcome.get("suggested").asInt()).isZero();
    }

    @Test
    @DisplayName("the categorizer being down is a note on the import, never a failed import")
    void categorizerDownIsANote() {
        when(categorizer.categorize(any(), anyString()))
            .thenThrow(new AiServiceClient.AiServiceException("The categorizer could not be reached."));

        importFixture();

        var batch = api.get("/api/v1/imports").json().get(0);
        assertThat(batch.get("status").asText()).isEqualTo("applied");
        assertThat(batch.get("appliedCount").asInt()).isEqualTo(6);
        assertThat(batch.get("warnings").toString()).contains("Rule suggestions were not available");
        assertThat(api.get("/api/v1/transactions/review").json().get("content")).hasSize(5);
    }
}
