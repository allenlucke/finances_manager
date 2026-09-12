package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import llc.feelingfroggy.finances.domain.Account;
import llc.feelingfroggy.finances.domain.Holding;
import llc.feelingfroggy.finances.domain.Security;
import llc.feelingfroggy.finances.repo.AccountRepository;
import llc.feelingfroggy.finances.repo.HoldingRepository;
import llc.feelingfroggy.finances.repo.SecurityRepository;
import llc.feelingfroggy.finances.support.ApiClient;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Holdings, and the balance rule they change (M4).
 *
 * <p>The load-bearing test here is {@link #brokerageValueComesFromHoldingsNotDeposits}. Everything
 * else is plumbing; that one is the difference between a correct net worth and one understated by
 * every dollar of market growth — with a figure that looks entirely plausible either way.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Holdings and market value")
class HoldingsTest extends PostgresIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountRepository accounts;
    @Autowired private SecurityRepository securities;
    @Autowired private HoldingRepository holdings;

    @LocalServerPort private int port;

    private ApiClient api;
    private Long userId;
    private Account brokerage;
    private Account checking;

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

        long brokerageId = api.postJson("/api/v1/accounts", Map.of("name", "Brokerage",
            "accountType", "BROKERAGE", "ledgerEntityId", entityId)).id();
        long checkingId = api.postJson("/api/v1/accounts", Map.of("name", "Checking",
            "accountType", "CHECKING", "ledgerEntityId", entityId)).id();
        brokerage = accounts.findById(brokerageId).orElseThrow();
        checking = accounts.findById(checkingId).orElseThrow();
    }

    private Security security(String symbol, boolean cash) {
        return securities.save(new Security(userId, symbol, symbol + " name",
            cash ? "money_market" : "equity", cash));
    }

    private Holding hold(Account account, Security security, String value, LocalDate asOf) {
        var holding = new Holding(userId, account, security, asOf, new BigDecimal(value));
        return holdings.save(holding);
    }

    private BigDecimal balance(long accountId) {
        return jdbc.queryForObject("SELECT balance FROM v_account_balance WHERE account_id = ?",
            BigDecimal.class, accountId);
    }

    private String balanceSource(long accountId) {
        return jdbc.queryForObject(
            "SELECT balance_source FROM v_account_balance WHERE account_id = ?",
            String.class, accountId);
    }

    @Test
    @DisplayName("a brokerage is worth its holdings, not the cash paid into it")
    void brokerageValueComesFromHoldingsNotDeposits() {
        // $10,000 deposited over time...
        api.postJson("/api/v1/transactions", Map.of("accountId", brokerage.getId(),
            "transactionDate", "2026-01-15", "amount", "10000.00", "direction", "credit",
            "description", "Electronic Funds Transfer Received"));
        assertThat(balance(brokerage.getId())).isEqualByComparingTo("10000.00");
        assertThat(balanceSource(brokerage.getId())).isEqualTo("transactions");

        // ...which has since grown to $14,000.
        hold(brokerage, security("FXAIX", false), "13100.00", LocalDate.of(2026, 8, 27));
        hold(brokerage, security("SPAXX", true), "900.00", LocalDate.of(2026, 8, 27));

        // The whole point. Summing deposits would report 10,000 and understate by every dollar of
        // growth — a wrong number that looks entirely reasonable.
        assertThat(balance(brokerage.getId())).isEqualByComparingTo("14000.00");
        assertThat(balanceSource(brokerage.getId())).isEqualTo("holdings");
    }

    @Test
    @DisplayName("a partial cost basis is no basis: one unknown position makes the account's null")
    void aPartialCostBasisIsReportedAsUnknown() {
        // SQL's SUM skips NULLs. Ten positions with two unknown bases used to report the other
        // eight as *the* basis, and market value minus that understated figure overstated the
        // gain with nothing to mark it. The view has said null since batch 1 of the first review;
        // nothing asserted it until now.
        var fund = hold(brokerage, security("FXAIX", false), "13100.00", LocalDate.of(2026, 8, 27));
        fund.setCostBasis(new BigDecimal("10000.00"));
        holdings.save(fund);
        hold(brokerage, security("SPAXX", true), "900.00", LocalDate.of(2026, 8, 27));

        var row = jdbc.queryForMap(
            "SELECT cost_basis, cost_basis_complete FROM v_account_market_value WHERE account_id = ?",
            brokerage.getId());

        assertThat(row.get("cost_basis")).isNull();
        assertThat(row.get("cost_basis_complete")).isEqualTo(false);
        assertThat(jdbc.queryForObject(
            "SELECT cost_basis FROM v_account_balance WHERE account_id = ?", BigDecimal.class,
            brokerage.getId())).isNull();
    }

    @Test
    @DisplayName("uninvested cash is inside the snapshot, so switching sources drops nothing")
    void cashRowsAreIncludedInMarketValue() {
        hold(brokerage, security("AAPL", false), "2200.00", LocalDate.of(2026, 8, 27));
        hold(brokerage, security("SPAXX", true), "900.00", LocalDate.of(2026, 8, 27));
        hold(brokerage, security("USD", true), "25.00", LocalDate.of(2026, 8, 27));

        assertThat(balance(brokerage.getId())).isEqualByComparingTo("3125.00");
    }

    @Test
    @DisplayName("only the newest snapshot counts, and older ones are kept")
    void supersededSnapshotsDoNotDoubleCount() {
        var fund = security("FXAIX", false);
        hold(brokerage, fund, "1000.00", LocalDate.of(2026, 7, 27));
        hold(brokerage, fund, "1200.00", LocalDate.of(2026, 8, 27));

        // Summing both snapshots would report 2200 — the classic snapshot double-count.
        assertThat(balance(brokerage.getId())).isEqualByComparingTo("1200.00");
        // The old one survives, because a position's history is the only thing a series of
        // snapshots can give you. The file itself has none.
        assertThat(holdings.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("each account keeps its own snapshot date")
    void snapshotDatesAreNotSharedBetweenAccounts() {
        // Importing one brokerage today and another next month must not hide the first behind the
        // second's newer date — the "latest" is per account, not overall.
        var second = accounts.save(new llc.feelingfroggy.finances.domain.Account(
            userId, brokerage.getLedgerEntity(), "Roth IRA",
            llc.feelingfroggy.finances.domain.AccountType.BROKERAGE));
        hold(brokerage, security("AAPL", false), "500.00", LocalDate.of(2026, 7, 1));
        hold(second, security("FXAIX", false), "700.00", LocalDate.of(2026, 8, 27));

        assertThat(balance(brokerage.getId())).isEqualByComparingTo("500.00");
        assertThat(balance(second.getId())).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("an account with no holdings still uses its transactions")
    void nonInvestmentAccountsAreUnaffected() {
        api.postJson("/api/v1/transactions", Map.of("accountId", checking.getId(),
            "transactionDate", "2026-08-14", "amount", "84.31", "direction", "debit",
            "description", "KROGER"));

        assertThat(balance(checking.getId())).isEqualByComparingTo("-84.31");
        assertThat(balanceSource(checking.getId())).isEqualTo("transactions");
    }

    @Test
    @DisplayName("net worth picks up market value without a special case")
    void netWorthUsesTheSameBalance() {
        hold(brokerage, security("FXAIX", false), "14000.00", LocalDate.of(2026, 8, 27));
        api.postJson("/api/v1/transactions", Map.of("accountId", checking.getId(),
            "transactionDate", "2026-08-14", "amount", "1000.00", "direction", "credit",
            "description", "Payroll"));

        BigDecimal combined = jdbc.queryForObject(
            "SELECT net_worth FROM v_net_worth WHERE ledger_entity_id IS NULL", BigDecimal.class);

        // v_net_worth sums v_account_balance, so it inherits the source switch with no CASE of
        // its own — the payoff of putting the rule in one view.
        assertThat(combined).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("net worth says how much of it is a snapshot, and how old the oldest one is")
    void netWorthSaysHowStaleItsSnapshotsAre() {
        // Before any snapshot: nothing to disclose.
        var before = api.get("/api/v1/reports/net-worth").json().get(0);
        assertThat(before.get("snapshotAccounts").asInt()).isZero();
        assertThat(before.get("oldestSnapshot").isNull()).isTrue();

        hold(brokerage, security("FXAIX", false), "14000.00", LocalDate.of(2026, 8, 27));
        // A deposit AFTER the snapshot. It is real money in the account, and it is not in net
        // worth: the brokerage is worth what the snapshot said until the next positions import.
        // That is exactly why the date has to travel with the number.
        api.postJson("/api/v1/transactions", Map.of("accountId", brokerage.getId(),
            "transactionDate", "2026-09-01", "amount", "10000.00", "direction", "credit",
            "description", "Electronic Funds Transfer Received"));

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT net_worth, snapshot_accounts, oldest_snapshot FROM v_net_worth "
            + "WHERE ledger_entity_id IS NULL");
        assertThat((BigDecimal) row.get("net_worth")).isEqualByComparingTo("14000.00");
        assertThat(((Number) row.get("snapshot_accounts")).intValue()).isEqualTo(1);
        assertThat(row.get("oldest_snapshot").toString()).isEqualTo("2026-08-27");

        var combined = api.get("/api/v1/reports/net-worth").json().get(0);
        assertThat(combined.get("snapshotAccounts").asInt()).isEqualTo(1);
        assertThat(combined.get("oldestSnapshot").asText()).isEqualTo("2026-08-27");
    }

    @Test
    @DisplayName("the API reports where a balance came from and how stale it is")
    void balanceProvenanceIsVisibleOverHttp() {
        hold(brokerage, security("FXAIX", false), "14000.00", LocalDate.of(2026, 8, 27));

        var accountsJson = api.get("/api/v1/accounts").json();
        var view = accountsJson.get(0).get("name").asText().equals("Brokerage")
            ? accountsJson.get(0) : accountsJson.get(1);

        assertThat(view.get("balanceSource").asText()).isEqualTo("holdings");
        assertThat(view.get("balanceAsOf").asText()).isEqualTo("2026-08-27");
    }

    @Test
    @DisplayName("holdings are readable, largest position first")
    void holdingsAreListed() {
        hold(brokerage, security("SPAXX", true), "900.00", LocalDate.of(2026, 8, 27));
        hold(brokerage, security("FXAIX", false), "13100.00", LocalDate.of(2026, 8, 27));

        var listed = api.get("/api/v1/holdings").json();

        assertThat(listed).hasSize(2);
        assertThat(listed.get(0).get("symbol").asText()).isEqualTo("FXAIX");
        assertThat(listed.get(0).get("marketValue").decimalValue()).isEqualByComparingTo("13100.00");
        assertThat(listed.get(1).get("cash").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a quantity keeps more precision than money does")
    void shareCountsAreNotRoundedToCents() {
        var fund = security("FXAIX", false);
        var holding = new Holding(userId, brokerage, fund, LocalDate.of(2026, 8, 27),
            new BigDecimal("2345.55"));
        // Mutual funds settle to three decimals and crypto to eight. Rounding a share count to
        // four would change what someone owns.
        holding.setQuantity(new BigDecimal("12.34567891"));
        holdings.save(holding);

        BigDecimal stored = jdbc.queryForObject(
            "SELECT quantity FROM holding WHERE id = ?", BigDecimal.class, holding.getId());

        assertThat(stored).isEqualByComparingTo("12.34567891");
    }
}
