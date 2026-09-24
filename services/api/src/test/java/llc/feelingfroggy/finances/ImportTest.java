package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.ParseResult;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.AccountType;
import llc.feelingfroggy.finances.domain.AppUser;
import llc.feelingfroggy.finances.domain.EntityKind;
import llc.feelingfroggy.finances.domain.LedgerEntity;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.repo.AppUserRepository;
import llc.feelingfroggy.finances.repo.LedgerEntityRepository;
import llc.feelingfroggy.finances.repo.TransactionRepository;
import llc.feelingfroggy.finances.service.ImportService;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

/**
 * The import pipeline: dedupe, idempotency, and the transfer flag.
 *
 * <p>The parser is mocked and fed a <strong>captured response from the real Python service</strong>
 * (`fixtures/parse-result-chase.json`). That keeps the test fast while still failing if the two
 * sides of the wire contract drift — the Java DTOs are hand-mirrored from
 * `finances_ai.models`, and a renamed field would otherwise deserialize to null and be silently
 * imported as missing data.
 *
 * <p><strong>Regenerate the fixture whenever the parser changes</strong>, from the CSV kept beside
 * it, so the drift guard guards against the parser that exists:
 * <pre>
 * cd services/ai && uv run python -c "from pathlib import Path; from finances_ai.ingest import parse_csv; \
 *   p = Path('../api/src/test/resources/fixtures/parse-result-chase'); \
 *   p.with_suffix('.json').write_text(parse_csv(p.with_suffix('.csv').read_bytes(), account_ref='chase-1234').model_dump_json(indent=2) + '\\n')"
 * </pre>
 * It was frozen at the pre-refund-split parser for two weeks, and in that state asserted that a
 * grocery refund is a transfer — the one thing the split exists to prevent.
 */
@DisplayName("Statement import")
class ImportTest extends PostgresIntegrationTest {

    @Autowired
    private ImportService imports;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private LedgerEntityRepository entities;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AiServiceClient aiService;

    private Long userId;
    private Account card;

    @BeforeEach
    void seed() throws IOException {
        cleanDatabase(jdbc);

        var user = users.save(new AppUser("owner@finances.invalid", "Owner", "x"));
        userId = user.getId();
        LedgerEntity personal =
            entities.save(new LedgerEntity(userId, "Personal", EntityKind.PERSONAL));
        card = accounts.save(new Account(userId, personal, "Chase Sapphire", AccountType.CREDIT_CARD));

        when(aiService.parseCsv(any(), any(), any())).thenReturn(realParserOutput());
    }

    /** Deserializes the captured Python response through the same DTOs production uses. */
    private static ParseResult realParserOutput() throws IOException {
        try (var stream = new ClassPathResource("fixtures/parse-result-chase.json").getInputStream()) {
            return new ObjectMapper().readValue(stream, ParseResult.class);
        }
    }

    private byte[] statementBytes() {
        return "irrelevant, the parser is mocked".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the parser is chosen from the file extension, with CSV as the fallback")
    void formatIsChosenFromTheExtension() {
        assertThat(llc.feelingfroggy.finances.service.ImportService.formatFor("chase.csv").code())
            .isEqualTo("csv");
        assertThat(llc.feelingfroggy.finances.service.ImportService.formatFor("STATEMENT.OFX").code())
            .isEqualTo("ofx");
        assertThat(llc.feelingfroggy.finances.service.ImportService.formatFor("export.qfx").code())
            .isEqualTo("qfx");
        // "Export to spreadsheet" produces a dozen names; CSV is the sane fallback.
        assertThat(llc.feelingfroggy.finances.service.ImportService.formatFor("download").code())
            .isEqualTo("csv");
        assertThat(llc.feelingfroggy.finances.service.ImportService.formatFor(null).code())
            .isEqualTo("csv");
    }

    @Test
    @DisplayName("the captured parser response maps onto the Java DTOs without losing a field")
    void wireContractHolds() throws IOException {
        ParseResult parsed = realParserOutput();

        assertThat(parsed.sourceFormat()).isEqualTo("chase_card");
        assertThat(parsed.transactions()).hasSize(6);

        var purchase = parsed.transactions().getFirst();
        // Every field the importer depends on must survive the snake_case boundary. A null here
        // would import as missing data rather than failing.
        assertThat(purchase.transactionDate()).isNotNull();
        assertThat(purchase.postedDate()).isNotNull();
        assertThat(purchase.amount()).isEqualByComparingTo("84.31");
        assertThat(purchase.direction()).isEqualTo("debit");
        assertThat(purchase.description()).isEqualTo("KROGER #4521");
        assertThat(purchase.merchant()).isEqualTo("KROGER");
        assertThat(purchase.dedupeKey()).isNotBlank();
        assertThat(purchase.isProbableTransfer()).isFalse();
        // The file's own word for the row, which is all that survives of `raw`.
        assertThat(purchase.sourceType()).isEqualTo("Sale");

        // The Chase Type column marks the payment as a transfer and the return as a refund — two
        // flags, because a transfer is not spending and a refund is negative spending.
        assertThat(parsed.transactions().stream().filter(t -> t.isProbableTransfer()).count())
            .isEqualTo(1);
        var refund = parsed.transactions().getLast();
        assertThat(refund.sourceType()).isEqualTo("Return");
        assertThat(refund.isProbableRefund()).isTrue();
        assertThat(refund.isProbableTransfer()).isFalse();
    }

    @Test
    @DisplayName("importing a statement lands every row once")
    void importsEveryRow() {
        var batch = imports.importStatement(userId, card, statementBytes(), "chase.csv").batch();

        assertThat(batch.getRowCount()).isEqualTo(6);
        assertThat(batch.getAppliedCount()).isEqualTo(6);
        assertThat(batch.getDuplicateCount()).isZero();
        assertThat(batch.getStatus().code()).isEqualTo("applied");

        Integer stored = jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class);
        assertThat(stored).isEqualTo(6);
    }

    @Test
    @DisplayName("importing the same statement twice adds nothing — the M2 'done when'")
    void reimportIsANoOp() {
        imports.importStatement(userId, card, statementBytes(), "chase.csv");
        var second = imports.importStatement(userId, card, statementBytes(), "chase.csv").batch();

        assertThat(second.getAppliedCount()).isZero();
        assertThat(second.getDuplicateCount()).isEqualTo(6);

        Integer stored = jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class);
        assertThat(stored).isEqualTo(6);
    }

    @Test
    @DisplayName("a card payment is imported as a transfer, so it never counts as spending")
    void paymentsAreImportedAsTransfers() {
        imports.importStatement(userId, card, statementBytes(), "chase.csv");

        Integer transfers = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE is_transfer", Integer.class);
        Integer categorized = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE is_transfer AND category_id IS NOT NULL",
            Integer.class);
        Integer refundsStillCategorizable = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE direction = 'credit' AND NOT is_transfer",
            Integer.class);

        // The Payment row only. The Return is a refund: negative spending that must stay
        // bookable against Groceries, or that category is overcharged for good.
        assertThat(transfers).isEqualTo(1);
        assertThat(refundsStillCategorizable).isEqualTo(1);
        // The double-count rule, enforced at import as well as by the CHECK constraint.
        assertThat(categorized).isZero();
    }

    @Test
    @DisplayName("the file's own word for each row is kept, not only the hint read from it")
    void sourceTypeIsKept() {
        imports.importStatement(userId, card, statementBytes(), "chase.csv");

        var byDescription = jdbc.queryForList(
            "SELECT description, source_type FROM transaction ORDER BY transaction_date DESC");

        assertThat(byDescription).extracting(row -> row.get("source_type"))
            .containsExactly("Sale", "Sale", "Payment", "Sale", "Sale", "Return");
    }

    @Test
    @DisplayName("imported rows carry their provenance")
    void rowsRecordWhereTheyCameFrom() {
        var batch = imports.importStatement(userId, card, statementBytes(), "chase.csv").batch();

        Integer traced = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE source = 'file_import' AND import_batch_id = ?",
            Integer.class, batch.getId());

        assertThat(traced).isEqualTo(6);
    }

    @Test
    @DisplayName("a deleted row is not resurrected by re-importing the same statement")
    void deletionSurvivesReimport() {
        imports.importStatement(userId, card, statementBytes(), "chase.csv");
        jdbc.update("UPDATE transaction SET deleted_at = now() WHERE description = 'NETFLIX.COM'");

        var second = imports.importStatement(userId, card, statementBytes(), "chase.csv").batch();

        // Deleting something was a decision; an import must not undo it.
        assertThat(second.getAppliedCount()).isZero();
        Integer live = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE deleted_at IS NULL", Integer.class);
        assertThat(live).isEqualTo(5);
    }

    @Test
    @DisplayName("the imported balance matches what the rows add up to")
    void balanceReflectsTheImport() {
        imports.importStatement(userId, card, statementBytes(), "chase.csv");

        BigDecimal balance = jdbc.queryForObject(
            "SELECT balance FROM v_account_balance WHERE account_id = ?", BigDecimal.class,
            card.getId());

        // -84.31 -46.02 +512.44 -22.99 -13.00 +12.00
        assertThat(balance).isEqualByComparingTo("358.12");
    }

    @Test
    @DisplayName("a parser failure marks the batch failed instead of half-importing")
    void parserFailureIsRecorded() {
        when(aiService.parseCsv(any(), any(), any()))
            .thenThrow(new AiServiceClient.AiServiceException("The statement could not be parsed."));

        try {
            imports.importStatement(userId, card, statementBytes(), "broken.csv");
        } catch (RuntimeException expected) {
            // The throw is the contract; what matters is what it left behind.
        }

        var batches = jdbc.queryForList("SELECT status, error FROM import_batch");
        assertThat(batches).hasSize(1);
        assertThat(batches.getFirst()).containsEntry("status", "failed");
        assertThat((String) batches.getFirst().get("error")).isNotBlank();

        Integer stored = jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class);
        assertThat(stored).isZero();
    }

    @Test
    @DisplayName("an import with a statement summary creates a reconciliation checkpoint")
    void statementSummaryBecomesACheckpoint() throws IOException {
        var parsed = realParserOutput();
        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult(
            parsed.sourceFormat(), parsed.transactions(), List.of(),
            new ParseResult.StatementSummary(
                java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 31),
                new BigDecimal("358.12"))));

        imports.importStatement(userId, card, statementBytes(), "chase.csv");

        var reconciliation = jdbc.queryForMap(
            "SELECT closing_balance, computed_balance, difference FROM v_statement_reconciliation");

        // The whole point of the checkpoint: the ledger agrees with the institution.
        assertThat((BigDecimal) reconciliation.get("difference")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a re-import whose balances differ replaces the checkpoint, and says so")
    void aDifferentCheckpointReplacesTheOldOne() throws IOException {
        // The first file was read the wrong way round (the same-date bug, now fixed) and stored
        // the oldest balance as the closing one. A checkpoint used to be permanent: unique per
        // period, created only by import, with no way to remove it — so the corrected re-import
        // could never put it right, and reconciliation accused a correct ledger for good.
        var parsed = realParserOutput();
        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult(
            parsed.sourceFormat(), parsed.transactions(), List.of(),
            new ParseResult.StatementSummary(java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 31), new BigDecimal("-12.00"))));
        imports.importStatement(userId, card, statementBytes(), "chase.csv");
        assertThat((BigDecimal) jdbc.queryForMap(
            "SELECT difference FROM v_statement_reconciliation").get("difference"))
            .isNotEqualByComparingTo("0");

        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult(
            parsed.sourceFormat(), parsed.transactions(), List.of(),
            new ParseResult.StatementSummary(java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 31), new BigDecimal("358.12"))));
        var again = imports.importStatement(userId, card, statementBytes(), "chase.csv").batch();

        Integer checkpoints = jdbc.queryForObject("SELECT count(*) FROM statement", Integer.class);
        assertThat(checkpoints).isEqualTo(1);
        assertThat((BigDecimal) jdbc.queryForMap(
            "SELECT difference FROM v_statement_reconciliation").get("difference"))
            .isEqualByComparingTo("0");
        // Said, not silent: a replaced figure is exactly the kind of thing to be told about.
        assertThat(again.getWarnings()).anySatisfy(note -> {
            assertThat(note).contains("Replaced the checkpoint");
            assertThat(note).contains("-12.00");
            assertThat(note).contains("358.12");
        });
    }

    @Test
    @DisplayName("re-importing does not create a second checkpoint for the same period")
    void checkpointIsNotDuplicated() throws IOException {
        var parsed = realParserOutput();
        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult(
            parsed.sourceFormat(), parsed.transactions(), List.of(),
            new ParseResult.StatementSummary(
                java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 8, 31),
                new BigDecimal("358.12"))));

        imports.importStatement(userId, card, statementBytes(), "chase.csv");
        imports.importStatement(userId, card, statementBytes(), "chase.csv");

        Integer checkpoints = jdbc.queryForObject("SELECT count(*) FROM statement", Integer.class);
        assertThat(checkpoints).isEqualTo(1);
    }
}
