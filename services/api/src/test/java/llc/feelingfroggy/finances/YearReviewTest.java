package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** A year in review: by category, per set of books, transfers out, the review queue said out loud. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Year in review")
class YearReviewTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @LocalServerPort private int port;

    private ApiClient api;
    private final int year = LocalDate.now(ZoneId.of("America/Chicago")).getYear();

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);
        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
    }

    @Test
    @DisplayName("sums by category for the year and the set of books, leaves transfers out, and names the uncategorized")
    void yearInReview() {
        var entities = api.get("/api/v1/entities").json();
        long personal = entities.get(0).get("id").asLong();
        long business = entities.get(1).get("id").asLong();
        long checking = api.postJson("/api/v1/accounts", Map.of("ledgerEntityId", personal, "name", "Checking",
            "accountType", "checking")).json().get("id").asLong();
        long savings = api.postJson("/api/v1/accounts", Map.of("ledgerEntityId", personal, "name", "Savings",
            "accountType", "savings")).json().get("id").asLong();
        long bizChecking = api.postJson("/api/v1/accounts", Map.of("ledgerEntityId", business, "name", "LLC checking",
            "accountType", "checking")).json().get("id").asLong();
        long groceries = api.postJson("/api/v1/categories", Map.of("name", "Groceries", "kind", "expense")).json().get("id").asLong();
        long salary = api.postJson("/api/v1/categories", Map.of("name", "Salary", "kind", "income")).json().get("id").asLong();
        long software = api.postJson("/api/v1/categories", Map.of("name", "Software", "kind", "expense")).json().get("id").asLong();

        String jan = year + "-01-15";
        String jun = year + "-06-15";
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", jan, "amount", "3000.00",
            "direction", "credit", "description", "Paycheck", "categoryId", salary));
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", jan, "amount", "120.50",
            "direction", "debit", "description", "Market", "categoryId", groceries));
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", jun, "amount", "80.00",
            "direction", "debit", "description", "Market again", "categoryId", groceries));
        // A refund against groceries reduces the year's spend.
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", jun, "amount", "20.00",
            "direction", "credit", "description", "Refund", "categoryId", groceries));
        // Uncategorized, and a transfer: one is named, the other left out.
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", jun, "amount", "45.00",
            "direction", "debit", "description", "Mystery"));
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", jun, "amount", "500.00",
            "direction", "debit", "description", "To savings", "transfer", true, "transferAccountId", savings));
        // Last year, and the business: not in this entity's year.
        api.postJson("/api/v1/transactions", Map.of("accountId", checking, "transactionDate", (year - 1) + "-12-30",
            "amount", "999.00", "direction", "debit", "description", "Old", "categoryId", groceries));
        api.postJson("/api/v1/transactions", Map.of("accountId", bizChecking, "transactionDate", jun, "amount", "300.00",
            "direction", "debit", "description", "IDE licence", "categoryId", software));

        var response = api.get("/api/v1/reports/year?year=" + year + "&ledgerEntityId=" + personal);
        assertThat(response.status()).as(response.body()).isEqualTo(200);
        var review = response.json();

        assertThat(new BigDecimal(review.get("income").asText())).isEqualByComparingTo("3000.00");
        assertThat(new BigDecimal(review.get("expenses").asText())).isEqualByComparingTo("180.50");
        assertThat(new BigDecimal(review.get("net").asText())).isEqualByComparingTo("2819.50");
        assertThat(new BigDecimal(review.get("uncategorizedOut").asText())).isEqualByComparingTo("45.00");
        assertThat(review.get("uncategorizedCount").asInt()).isEqualTo(1);
        assertThat(review.get("entityName").asText()).isEqualTo(entities.get(0).get("name").asText());
        var groceriesRow = review.get("categories").get(0).get("name").asText().equals("Groceries")
            ? review.get("categories").get(0) : review.get("categories").get(1);
        assertThat(new BigDecimal(groceriesRow.get("amount").asText())).isEqualByComparingTo("180.50");
        assertThat(groceriesRow.get("count").asInt()).isEqualTo(3);
        assertThat(review.toString()).doesNotContain("Software");

        var csv = api.get("/api/v1/reports/year.csv?year=" + year + "&ledgerEntityId=" + personal);
        assertThat(csv.status()).isEqualTo(200);
        assertThat(csv.body()).startsWith("kind,category,rows,amount\n");
        assertThat(csv.body()).contains("expense,Groceries,3,180.50\n").contains("income,Salary,1,3000.00\n");
        assertThat(csv.body()).contains("summary,net,,2819.50\n").contains("summary,uncategorized out,1,45.00\n");

        var everything = api.get("/api/v1/reports/year?year=" + year).json();
        assertThat(new BigDecimal(everything.get("expenses").asText())).isEqualByComparingTo("480.50");
        assertThat(everything.get("entityName").isNull()).isTrue();
        assertThat(new BigDecimal(api.get("/api/v1/reports/year?year=" + (year - 1)).json().get("expenses").asText()))
            .isEqualByComparingTo("999.00");
    }
}
