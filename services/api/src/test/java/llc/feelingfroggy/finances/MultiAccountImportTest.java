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
import llc.feelingfroggy.finances.ai.ParsedTransaction;
import llc.feelingfroggy.finances.ai.ParseResult;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.AccountType;
import llc.feelingfroggy.finances.domain.AppUser;
import llc.feelingfroggy.finances.domain.EntityKind;
import llc.feelingfroggy.finances.domain.LedgerEntity;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.repo.AppUserRepository;
import llc.feelingfroggy.finances.repo.LedgerEntityRepository;
import llc.feelingfroggy.finances.service.ImportService;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Importing an export that covers several accounts — a brokerage history, where one file holds a
 * joint account and two children's UTMAs.
 *
 * <p>The rule throughout: a row goes to the account it names, or nowhere. Filing brokerage activity
 * against the wrong account is silent and corrupts two balances at once, so an unimported row —
 * visible and fixable — is always the better failure.
 */
@DisplayName("Multi-account import")
class MultiAccountImportTest extends PostgresIntegrationTest {

    @Autowired
    private ImportService imports;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private LedgerEntityRepository entities;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AiServiceClient aiService;

    private Long userId;
    private LedgerEntity personal;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM categorization");
        jdbc.update("DELETE FROM transaction");
        jdbc.update("DELETE FROM statement");
        jdbc.update("DELETE FROM import_batch");
        jdbc.update("DELETE FROM account");
        jdbc.update("DELETE FROM ledger_entity");
        jdbc.update("DELETE FROM app_user");

        userId = users.save(new AppUser("allen@feelingfroggy.llc", "Allen", "x")).getId();
        personal = entities.save(new LedgerEntity(userId, "Personal", EntityKind.PERSONAL));
    }

    private Account account(String name, String mask) {
        var created = new Account(userId, personal, name, AccountType.BROKERAGE);
        created.setMask(mask);
        return accounts.save(created);
    }

    /** A row as the brokerage parser produces it: it names its own account. */
    private ParsedTransaction row(String mask, String key, String description, String amount) {
        return new ParsedTransaction(LocalDate.of(2026, 8, 24), null, description, description,
            new BigDecimal(amount), "credit", null, "dedupe-" + key + "-" + description,
            true, mask, key, "Account " + mask, Map.of());
    }

    private void parserReturns(ParsedTransaction... rows) {
        when(aiService.parseCsv(any(), any(), any()))
            .thenReturn(new ParseResult("fidelity_history", List.of(rows), List.of(), null));
    }

    private byte[] anyFile() {
        return "parser is mocked".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("rows land in the account they name, not the one passed in")
    void rowsGoToTheAccountTheyName() {
        var joint = account("Long Term Investment", "1111");
        var minorOne = account("Minor One UTMA", "2222");
        parserReturns(
            row("1111", "keyJoint", "Transfer Received", "1000"),
            row("2222", "keyMinorOne", "Transfer Received", "50"));

        // Deliberately passing no account: the file identifies its own.
        var batch = imports.importStatement(userId, null, anyFile(), "Accounts_History.csv");

        assertThat(batch.getAppliedCount()).isEqualTo(2);
        assertThat(countFor(joint)).isEqualTo(1);
        assertThat(countFor(minorOne)).isEqualTo(1);
    }

    @Test
    @DisplayName("a named account overrides whatever account was passed in")
    void theFileWinsOverTheCaller() {
        var joint = account("Long Term Investment", "1111");
        var minorOne = account("Minor One UTMA", "2222");
        parserReturns(row("2222", "keyMinorOne", "Transfer Received", "50"));

        // The caller nominates the joint account; the row says it belongs to the first minor's.
        imports.importStatement(userId, joint, anyFile(), "Accounts_History.csv");

        assertThat(countFor(joint)).isZero();
        assertThat(countFor(minorOne)).isEqualTo(1);
    }

    @Test
    @DisplayName("an unmatched account is skipped and reported, never filed somewhere plausible")
    void unmatchedRowsAreSkippedAndReported() {
        var joint = account("Long Term Investment", "1111");
        parserReturns(
            row("1111", "keyJoint", "Transfer Received", "1000"),
            row("9999", "keyUnknown", "Transfer Received", "500"));

        var batch = imports.importStatement(userId, null, anyFile(), "Accounts_History.csv");

        assertThat(batch.getAppliedCount()).isEqualTo(1);
        assertThat(countFor(joint)).isEqualTo(1);
        // The user is told which account is missing, so it can be created and the file re-imported.
        assertThat(batch.getError()).contains("9999");
        Integer total = jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class);
        assertThat(total).isEqualTo(1);
    }

    @Test
    @DisplayName("matching by mask once records the key, so later imports match exactly")
    void theAccountLinkIsLearnedOnce() {
        var joint = account("Long Term Investment", "1111");
        assertThat(joint.getExternalId()).isNull();

        parserReturns(row("1111", "keyJoint", "Transfer Received", "1000"));
        imports.importStatement(userId, null, anyFile(), "Accounts_History.csv");

        var linked = accounts.findByIdAndUserId(joint.getId(), userId).orElseThrow();
        assertThat(linked.getExternalId()).isEqualTo("keyJoint");
    }

    @Test
    @DisplayName("an ambiguous mask is refused rather than resolved by picking one")
    void ambiguousMasksAreNotGuessed() {
        // Two accounts genuinely can end in the same four digits.
        account("Minor One UTMA", "2222");
        account("Minor Two UTMA", "2222");
        parserReturns(row("2222", "keyChild", "Transfer Received", "50"));

        var batch = imports.importStatement(userId, null, anyFile(), "Accounts_History.csv");

        assertThat(batch.getAppliedCount()).isZero();
        assertThat(batch.getError()).contains("2222");
    }

    @Test
    @DisplayName("identical activity in two accounts stays two transactions")
    void sameAmountInTwoAccountsDoesNotCollide() {
        account("Minor One UTMA", "2222");
        account("Minor Two UTMA", "3333");
        parserReturns(
            row("2222", "keyMinorOne", "Transfer Received", "50"),
            row("3333", "keyMinorTwo", "Transfer Received", "50"));

        var batch = imports.importStatement(userId, null, anyFile(), "Accounts_History.csv");

        // Two children receiving the same amount on the same day is ordinary.
        assertThat(batch.getAppliedCount()).isEqualTo(2);
        assertThat(batch.getDuplicateCount()).isZero();
    }

    @Test
    @DisplayName("re-importing a multi-account file adds nothing")
    void reimportIsStillIdempotent() {
        account("Long Term Investment", "1111");
        account("Minor One UTMA", "2222");
        parserReturns(
            row("1111", "keyJoint", "Transfer Received", "1000"),
            row("2222", "keyMinorOne", "Transfer Received", "50"));

        imports.importStatement(userId, null, anyFile(), "Accounts_History.csv");
        var second = imports.importStatement(userId, null, anyFile(), "Accounts_History.csv");

        assertThat(second.getAppliedCount()).isZero();
        assertThat(second.getDuplicateCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("brokerage rows are imported as transfers, never as spending")
    void brokerageRowsAreNotSpending() {
        account("Long Term Investment", "1111");
        parserReturns(row("1111", "keyJoint", "YOU BOUGHT FXAIX", "1000"));

        imports.importStatement(userId, null, anyFile(), "Accounts_History.csv");

        Integer spending = jdbc.queryForObject(
            "SELECT count(*) FROM transaction WHERE NOT is_transfer", Integer.class);
        assertThat(spending).isZero();
    }

    @Test
    @DisplayName("a single-account file with no account chosen fails rather than guessing")
    void singleAccountFileNeedsAnAccount() {
        account("Checking", "9876");
        // A plain bank CSV: no row names an account.
        when(aiService.parseCsv(any(), any(), any())).thenReturn(new ParseResult("cacu",
            List.of(new ParsedTransaction(LocalDate.of(2026, 8, 24), null, "KROGER", "KROGER",
                new BigDecimal("84.31"), "debit", null, "dk1", false, null, null, null, Map.of())),
            List.of(), null));

        try {
            imports.importStatement(userId, null, anyFile(), "cacu.csv");
        } catch (RuntimeException expected) {
            // Expected: nothing in the file says where these belong.
        }

        Integer stored = jdbc.queryForObject("SELECT count(*) FROM transaction", Integer.class);
        assertThat(stored).isZero();
    }

    private Integer countFor(Account account) {
        return jdbc.queryForObject("SELECT count(*) FROM transaction WHERE account_id = ?",
            Integer.class, account.getId());
    }
}
