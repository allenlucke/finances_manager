package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import llc.feelingfroggy.finances.ai.AiServiceClient;
import llc.feelingfroggy.finances.ai.ParseResult;
import llc.feelingfroggy.finances.ai.ParsedTransaction;
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
 * The import workflow as the browser drives it: a multipart upload, over HTTP, through the
 * controller.
 *
 * <p>Review finding F3. Every other import test called {@code ImportService} directly, so the
 * request parsing, the status codes and the response shape the screen depends on — unlinked
 * accounts above all — had never been exercised by anything automated. The parser is stubbed;
 * what is under test is everything on this side of it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Statement import over HTTP")
class ImportHttpTest extends PostgresIntegrationTest {

    private static final byte[] A_FILE = "Date,Description,Amount\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private AiServiceClient aiService;
    @LocalServerPort private int port;

    private ApiClient api;
    private long entityId;
    private long cardId;

    @BeforeEach
    void setUp() {
        cleanDatabase(jdbc);

        api = new ApiClient(port);
        api.primeCsrf();
        api.postJson("/api/v1/setup", Map.of("email", "allen@feelingfroggy.llc",
            "displayName", "Allen", "password", "a-long-enough-passphrase"));
        api.login("allen@feelingfroggy.llc", "a-long-enough-passphrase");
        entityId = api.get("/api/v1/entities").json().get(0).get("id").asLong();
        cardId = api.postJson("/api/v1/accounts", Map.of("name", "Card",
            "accountType", "CREDIT_CARD", "ledgerEntityId", entityId)).id();
    }

    private static ParsedTransaction row(String date, String amount, String description) {
        return new ParsedTransaction(LocalDate.parse(date), null, description, description,
            new BigDecimal(amount), "debit", null, "parser-key-" + description, false, false,
            null, null, null, Map.of());
    }

    /** A row from an export that names its own account, as a brokerage history does. */
    private static ParsedTransaction rowFor(String key, String mask, String name, String date,
                                            String amount, String description) {
        return new ParsedTransaction(LocalDate.parse(date), null, description, description,
            new BigDecimal(amount), "debit", null, "parser-key-" + description, false, false,
            mask, key, name, Map.of());
    }

    private void parserReturns(ParsedTransaction... rows) {
        when(aiService.parseCsv(any(), any(), any()))
            .thenReturn(new ParseResult("generic", List.of(rows), List.of(), null));
    }

    private ApiClient.Response upload(Map<String, String> params) {
        return api.postFile("/api/v1/imports", "file", "statement.csv", A_FILE, params);
    }

    @Test
    @DisplayName("an upload lands its rows, and the same upload again is all duplicates")
    void uploadThenReupload() {
        parserReturns(row("2026-08-14", "84.31", "KROGER"), row("2026-08-15", "4.50", "COFFEE"));

        var first = upload(Map.of("accountId", String.valueOf(cardId)));

        assertThat(first.status()).isEqualTo(201);
        var body = first.json();
        assertThat(body.get("status").asText()).isEqualTo("applied");
        assertThat(body.get("filename").asText()).isEqualTo("statement.csv");
        assertThat(body.get("rowCount").asInt()).isEqualTo(2);
        assertThat(body.get("appliedCount").asInt()).isEqualTo(2);
        assertThat(body.get("duplicateCount").asInt()).isZero();
        assertThat(body.get("unlinkedAccounts")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class))
            .isEqualTo(2);

        var second = upload(Map.of("accountId", String.valueOf(cardId)));

        assertThat(second.status()).isEqualTo(201);
        assertThat(second.json().get("appliedCount").asInt()).isZero();
        assertThat(second.json().get("duplicateCount").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class))
            .isEqualTo(2);

        // Both attempts are on record, in the order the screen lists them.
        var history = api.get("/api/v1/imports").json();
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("a file that names an account this system lacks reports it, so the screen can create it")
    void unlinkedAccountsAreReportedThenResolvedByCreatingThem() {
        parserReturns(
            rowFor("key-1", "8901", "Cashback Free Checking", "2026-08-14", "84.31", "KROGER"),
            rowFor("key-1", "8901", "Cashback Free Checking", "2026-08-15", "4.50", "COFFEE"));

        // No accountId: the file says where its rows belong.
        var first = upload(Map.of());

        assertThat(first.status()).isEqualTo(201);
        var unlinked = first.json().get("unlinkedAccounts");
        assertThat(unlinked).hasSize(1);
        assertThat(unlinked.get(0).get("key").asText()).isEqualTo("key-1");
        assertThat(unlinked.get(0).get("mask").asText()).isEqualTo("8901");
        assertThat(unlinked.get(0).get("name").asText()).isEqualTo("Cashback Free Checking");
        assertThat(unlinked.get(0).get("transactionCount").asInt()).isEqualTo(2);
        // Nothing was filed against a guessed account.
        assertThat(first.json().get("appliedCount").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class)).isZero();

        // What the screen's create-and-retry does: create the account with the file's own link,
        // then send the same file again.
        var created = api.postJson("/api/v1/accounts", Map.of("name", "Cashback Free Checking",
            "accountType", "CHECKING", "ledgerEntityId", entityId, "mask", "8901",
            "externalId", "key-1"));
        assertThat(created.status()).isEqualTo(201);

        var retry = upload(Map.of());

        assertThat(retry.status()).isEqualTo(201);
        assertThat(retry.json().get("unlinkedAccounts")).isEmpty();
        assertThat(retry.json().get("appliedCount").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE account_id = ?", Integer.class, created.id()))
            .isEqualTo(2);
    }

    @Test
    @DisplayName("a file that names no account, uploaded with none chosen, is refused with a reason")
    void anonymousFileWithNoAccountIsRefused() {
        parserReturns(row("2026-08-14", "84.31", "KROGER"));

        var response = upload(Map.of());

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body()).contains("does not name an account");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class)).isZero();
        // The batch is on record as failed, with the reason — in its own transaction, so the
        // refusal did not erase the evidence of itself.
        var batch = api.get("/api/v1/imports").json().get(0);
        assertThat(batch.get("status").asText()).isEqualTo("failed");
        assertThat(batch.get("error").asText()).contains("does not name an account");
    }

    @Test
    @DisplayName("a parser rejection is a 422 with the parser's sentence, and a failed batch")
    void parserRejectionIsAnOutcomeNotAFault() {
        when(aiService.parseCsv(any(), any(), any()))
            .thenThrow(new AiServiceClient.AiServiceException("No parser matches this file."));

        var response = upload(Map.of("accountId", String.valueOf(cardId)));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body()).contains("No parser matches this file.");
        var batch = api.get("/api/v1/imports").json().get(0);
        assertThat(batch.get("status").asText()).isEqualTo("failed");
    }

    @Test
    @DisplayName("what the parser could not read reaches the response and the history")
    void parserWarningsReachThePerson() {
        // Review 2026-09-11 J1: these were counted into a log line and dropped, so a file that
        // lost rows to a bad date looked exactly like one that did not.
        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult("generic",
            List.of(row("2026-08-14", "84.31", "KROGER")),
            List.of("line 3: Unrecognized date 'nope' (tried %m/%d/%Y, %Y-%m-%d)"), null));

        var response = upload(Map.of("accountId", String.valueOf(cardId)));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json().get("warnings")).hasSize(1);
        assertThat(response.json().get("warnings").get(0).asText()).contains("line 3");
        var history = api.get("/api/v1/imports").json().get(0);
        assertThat(history.get("warnings").get(0).asText()).contains("line 3");
    }

    @Test
    @DisplayName("a reconciliation row says it summed the whole history when it had no opening balance")
    void reconciliationSaysWhenItSummedTheWholeHistory() {
        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult("cacu",
            List.of(row("2026-08-14", "84.31", "KROGER")), List.of(),
            new ParseResult.StatementSummary(LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-31"), new BigDecimal("-84.31"))));
        upload(Map.of("accountId", String.valueOf(cardId)));

        var rows = api.get("/api/v1/reports/reconciliation").json();

        assertThat(rows).hasSize(1);
        // The view has said this since the opening-balance work; the endpoint did not pass it
        // on, so the dashboard's "summed from the beginning" qualifier could never render.
        assertThat(rows.get(0).get("baseline").asText()).isEqualTo("full_history");
        assertThat(new BigDecimal(rows.get(0).get("difference").asText())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a reconciliation row says it started from the statement's own opening balance")
    void reconciliationSaysWhenItUsedTheOpeningBalance() {
        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult("cacu",
            List.of(row("2026-08-14", "84.31", "KROGER")), List.of(),
            new ParseResult.StatementSummary(LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-31"), new BigDecimal("100.00"), new BigDecimal("15.69"))));
        upload(Map.of("accountId", String.valueOf(cardId)));

        var rows = api.get("/api/v1/reports/reconciliation").json();

        assertThat(rows.get(0).get("baseline").asText()).isEqualTo("opening_balance");
        assertThat(new BigDecimal(rows.get(0).get("difference").asText())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an unknown account id is a 400, not a guess")
    void unknownAccountIsRefused() {
        parserReturns(row("2026-08-14", "84.31", "KROGER"));

        var response = upload(Map.of("accountId", "999999"));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("an empty upload is refused before the parser is called")
    void emptyFileIsRefused() {
        var response = api.postFile("/api/v1/imports", "file", "empty.csv", new byte[0],
            Map.of("accountId", String.valueOf(cardId)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(api.get("/api/v1/imports").json()).isEmpty();
    }
}
