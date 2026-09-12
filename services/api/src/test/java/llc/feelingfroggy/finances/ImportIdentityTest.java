package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.ParseResult;
import llc.feelingfroggy.finances.ai.ParsedTransaction;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.Direction;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.service.ImportService;
import llc.feelingfroggy.finances.service.TransactionService;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Who decides what a transaction <em>is</em>, and what happens when an import fails halfway.
 *
 * <p>Review findings A1, A2, A3, A12 and G2 (docs/REVIEW-2026-08-29.md). The common thread: a
 * plausible-looking wrong number, produced silently.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Transaction identity and import atomicity")
class ImportIdentityTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ImportService imports;
    @Autowired private TransactionService transactions;
    @Autowired private AccountRepository accounts;
    @MockitoBean private AiServiceClient aiService;
    @LocalServerPort private int port;

    private ApiClient api;
    private Long userId;
    private Account card;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);

        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "owner@finances.invalid",
            "displayName", "Owner", "password", "a-long-enough-passphrase"));
        api.login("owner@finances.invalid", "a-long-enough-passphrase");
        userId = jdbc.queryForObject("SELECT id FROM app_user", Long.class);
        long entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        long cardId = api.postJson("/api/v1/accounts", Map.of("name", "Card",
            "accountType", "CREDIT_CARD", "ledgerEntityId", entityId)).id();
        card = accounts.findById(cardId).orElseThrow();
    }

    /** A parser row. The parser's own dedupe_key is deliberately nonsense: the API must not read it. */
    private static ParsedTransaction row(String date, String amount, String direction,
                                         String description, String externalId,
                                         boolean transfer, boolean refund) {
        return new ParsedTransaction(LocalDate.parse(date), null, description, description,
            new BigDecimal(amount), direction, externalId,
            "parser-key-" + java.util.Objects.hashCode(description),
            transfer, refund, null, null, null);
    }

    /** Whichever parser the filename selects, since the identity rule must hold for both. */
    private void parserReturns(ParsedTransaction... rows) {
        var result = new ParseResult("chase_card", List.of(rows), List.of(), null);
        when(aiService.parseCsv(any(), any(), any())).thenReturn(result);
        when(aiService.parseOfx(any(), any(), any())).thenReturn(result);
    }

    private int liveRows() {
        return jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE deleted_at IS NULL", Integer.class);
    }

    @Test
    @DisplayName("a debit entered by hand is recognised when the statement later contains it")
    void manualDebitCollidesWithItsImport() {
        // The most common way a duplicate entered the ledger: buy something, enter it, then
        // import the statement two weeks later. The Python key hashed the signed amount and the
        // Java key the magnitude, so no debit could ever collide with itself.
        transactions.record(userId, card, LocalDate.of(2026, 8, 14), new BigDecimal("84.31"),
            Direction.DEBIT, "KROGER #4521", null);
        parserReturns(row("2026-08-14", "84.31", "debit", "Kroger #4521", null, false, false));

        var outcome = imports.importStatement(userId, card, new byte[] {1}, "chase.csv");

        assertThat(outcome.batch().getDuplicateCount()).isEqualTo(1);
        assertThat(outcome.batch().getAppliedCount()).isZero();
        assertThat(liveRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("reference numbers are part of the identity")
    void referenceNumbersDistinguishTransactions() {
        // CHECK #1234 and CHECK #5678, same day, same amount, are two checks. The parser's
        // normalization stripped the number and made them one.
        parserReturns(
            row("2026-08-14", "250.00", "debit", "CHECK #1234", null, false, false),
            row("2026-08-14", "250.00", "debit", "CHECK #5678", null, false, false));

        var outcome = imports.importStatement(userId, card, new byte[] {1}, "checks.csv");

        assertThat(outcome.batch().getAppliedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("the institution's own id wins over every visible field")
    void institutionIdIsTheIdentity() {
        // Three identical same-day transfers, distinguishable only by the bank's id. Hashing the
        // visible fields collapsed them and lost the money.
        parserReturns(
            row("2026-08-14", "1000.00", "debit", "TRANSFER TO SAVINGS", "T1", false, false),
            row("2026-08-14", "1000.00", "debit", "TRANSFER TO SAVINGS", "T2", false, false),
            row("2026-08-14", "1000.00", "debit", "TRANSFER TO SAVINGS", "T3", false, false));

        var outcome = imports.importStatement(userId, card, new byte[] {1}, "bank.ofx");

        assertThat(outcome.batch().getAppliedCount()).isEqualTo(3);
        assertThat(liveRows()).isEqualTo(3);
    }

    @Test
    @DisplayName("a refund is a credit that can be categorized, not a transfer")
    void refundsStayCategorizable() {
        parserReturns(row("2026-08-09", "12.00", "credit", "KROGER #4521", null, false, true));
        long categoryId = api.postJson("/api/v1/categories",
            Map.of("name", "Groceries", "kind", "EXPENSE")).id();

        imports.importStatement(userId, card, new byte[] {1}, "chase.csv");
        long id = jdbc.queryForObject("SELECT id FROM transaction", Long.class);

        // Used to be marked as a transfer, which the CHECK constraint makes uncategorizable, so
        // the refund could never give Groceries its $12 back.
        assertThat(jdbc.queryForObject("SELECT is_transfer FROM transaction", Boolean.class)).isFalse();
        assertThat(api.putJson("/api/v1/transactions/" + id + "/category",
            Map.of("categoryId", categoryId)).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("a card payment is still a transfer")
    void paymentsAreStillTransfers() {
        parserReturns(row("2026-08-10", "512.44", "credit", "Payment Thank You", null, true, false));

        imports.importStatement(userId, card, new byte[] {1}, "chase.csv");

        assertThat(jdbc.queryForObject("SELECT is_transfer FROM transaction", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("a row the database refuses fails the whole batch, with a reason, and keeps nothing")
    void aRefusedRowFailsTheBatchAtomically() {
        // A null description violates NOT NULL at flush. Before: the exception surfaced at commit,
        // past the guard around the parser call, the rows rolled back, and the batch sat PENDING
        // with no error — while a REQUIRES_NEW complete() had already committed it as APPLIED.
        parserReturns(
            row("2026-08-14", "84.31", "debit", "KROGER", null, false, false),
            row("2026-08-13", "10.00", "debit", null, null, false, false));

        assertThatThrownBy(() -> imports.importStatement(userId, card, new byte[] {1}, "bad.csv"))
            .isInstanceOf(RuntimeException.class);

        assertThat(liveRows()).isZero();
        var batch = jdbc.queryForMap("SELECT status, error, applied_count FROM import_batch");
        assertThat(batch.get("status")).isEqualTo("failed");
        assertThat(batch.get("applied_count")).isEqualTo(0);
        assertThat((String) batch.get("error"))
            .isNotBlank()
            // A person reads this in import history: a sentence, never a constraint name.
            .doesNotContainIgnoringCase("constraint")
            .doesNotContainIgnoringCase("null value");
    }

    @Test
    @DisplayName("a statement's opening balance is kept, so reconciliation has a baseline")
    void openingBalanceReachesTheCheckpoint() {
        var summary = new ParseResult.StatementSummary(LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31), new BigDecimal("500.00"), new BigDecimal("415.69"));
        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult("cacu",
            List.of(row("2026-08-14", "84.31", "debit", "KROGER", null, false, false)),
            List.of(), summary));

        imports.importStatement(userId, card, new byte[] {1}, "cacu.csv");

        var stmt = jdbc.queryForMap("SELECT opening_balance, closing_balance FROM statement");
        assertThat((BigDecimal) stmt.get("opening_balance")).isEqualByComparingTo("500.00");
        assertThat((BigDecimal) stmt.get("closing_balance")).isEqualByComparingTo("415.69");
    }
}
