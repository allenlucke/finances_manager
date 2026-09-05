package llc.feelingfroggy.finances;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import llc.feelingfroggy.finances.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The reporting views are where a wrong number would actually reach Allen, so they are tested
 * against real data rather than trusted.
 *
 * <p>Uses {@link JdbcTemplate} rather than the repositories on purpose: the point is to verify the
 * SQL in {@code R__reporting_views.sql}, and going through JPA would test the mapping instead.
 */
@DisplayName("Reporting views")
class ReportingViewsTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private long personalId;
    private long businessId;
    private long cardId;
    private long checkingId;
    private long groceriesId;

    @BeforeEach
    void seed() {
        // Order matters: transaction references everything else.
        cleanDatabase(jdbc);

        userId = insert("INSERT INTO app_user (email, display_name, password_hash) "
            + "VALUES ('allen@feelingfroggy.llc', 'Allen', 'x') RETURNING id");
        personalId = insert("INSERT INTO ledger_entity (user_id, name, kind) "
            + "VALUES (" + userId + ", 'Personal', 'personal') RETURNING id");
        businessId = insert("INSERT INTO ledger_entity (user_id, name, kind) "
            + "VALUES (" + userId + ", 'Feeling Froggy LLC', 'business') RETURNING id");
        cardId = insert("INSERT INTO account (user_id, ledger_entity_id, name, account_type) "
            + "VALUES (" + userId + ", " + personalId + ", 'Chase Sapphire', 'credit_card') RETURNING id");
        checkingId = insert("INSERT INTO account (user_id, ledger_entity_id, name, account_type) "
            + "VALUES (" + userId + ", " + personalId + ", 'Checking', 'checking') RETURNING id");
        groceriesId = insert("INSERT INTO category (user_id, name, kind) "
            + "VALUES (" + userId + ", 'Groceries', 'expense') RETURNING id");
    }

    private long insert(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private void txn(long accountId, String date, String amount, String direction, String desc,
                     Long categoryId, String key) {
        jdbc.update("""
            INSERT INTO transaction
              (user_id, account_id, category_id, transaction_date, amount, direction, description, dedupe_key)
            VALUES (?, ?, ?, ?::date, ?::numeric, ?, ?, ?)
            """, userId, accountId, categoryId, date, amount, direction, desc, key);
    }

    @Test
    @DisplayName("a credit-card balance is negative when owed, and net worth is a plain sum")
    void balancesAndNetWorth() {
        txn(checkingId, "2026-08-01", "3000.00", "credit", "Paycheck", null, "k1");
        txn(cardId, "2026-08-05", "500.00", "debit", "Card purchase", groceriesId, "k2");

        Map<String, Object> card = jdbc.queryForMap(
            "SELECT balance FROM v_account_balance WHERE account_id = ?", cardId);
        Map<String, Object> checking = jdbc.queryForMap(
            "SELECT balance FROM v_account_balance WHERE account_id = ?", checkingId);

        // Spending on a card makes its balance negative: negative means owed.
        assertThat((BigDecimal) card.get("balance")).isEqualByComparingTo("-500.00");
        assertThat((BigDecimal) checking.get("balance")).isEqualByComparingTo("3000.00");

        BigDecimal netWorth = jdbc.queryForObject(
            "SELECT net_worth FROM v_net_worth WHERE user_id = ? AND ledger_entity_id IS NULL",
            BigDecimal.class, userId);

        // 3000 - 500, with no CASE on account_type anywhere.
        assertThat(netWorth).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("an account with no transactions reports zero rather than disappearing")
    void emptyAccountStillListed() {
        BigDecimal balance = jdbc.queryForObject(
            "SELECT balance FROM v_account_balance WHERE account_id = ?", BigDecimal.class, cardId);

        assertThat(balance).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("paying the card is not spending — the double-count rule, in SQL")
    void cardPaymentIsNotSpend() {
        // Buy groceries on the card: this is the spend, and the budget is charged here.
        txn(cardId, "2026-08-05", "500.00", "debit", "KROGER", groceriesId, "k1");
        // Then pay the card from checking. The money moves again, but nothing new was bought.
        jdbc.update("""
            INSERT INTO transaction
              (user_id, account_id, transaction_date, amount, direction, description,
               is_transfer, transfer_account_id, dedupe_key)
            VALUES (?, ?, '2026-08-20'::date, 500.00, 'debit', 'Payment Thank You', TRUE, ?, 'k2')
            """, userId, checkingId, cardId);

        BigDecimal spend = jdbc.queryForObject("""
            SELECT net_amount FROM v_monthly_category_spend
            WHERE category_id = ? AND month = '2026-08-01'::date
            """, BigDecimal.class, groceriesId);

        // 500, not 1000. Counting the payment too would double-charge the budget.
        assertThat(spend).isEqualByComparingTo("500.00");

        // ...while both accounts still move, so net worth is unchanged by the payment itself.
        BigDecimal netWorth = jdbc.queryForObject(
            "SELECT net_worth FROM v_net_worth WHERE user_id = ? AND ledger_entity_id IS NULL",
            BigDecimal.class, userId);
        assertThat(netWorth).isEqualByComparingTo("-1000.00");
    }

    @Test
    @DisplayName("a refund reduces spend rather than adding to it")
    void refundReducesSpend() {
        txn(cardId, "2026-08-05", "500.00", "debit", "KROGER", groceriesId, "k1");
        txn(cardId, "2026-08-07", "120.00", "credit", "KROGER refund", groceriesId, "k2");

        BigDecimal spend = jdbc.queryForObject("""
            SELECT net_amount FROM v_monthly_category_spend
            WHERE category_id = ? AND month = '2026-08-01'::date
            """, BigDecimal.class, groceriesId);

        assertThat(spend).isEqualByComparingTo("380.00");
    }

    @Test
    @DisplayName("a soft-deleted transaction stops counting toward balances")
    void softDeleteExcludedFromReports() {
        txn(checkingId, "2026-08-01", "100.00", "debit", "Mistake", groceriesId, "k1");
        jdbc.update("UPDATE transaction SET deleted_at = now() WHERE dedupe_key = 'k1'");

        BigDecimal balance = jdbc.queryForObject(
            "SELECT balance FROM v_account_balance WHERE account_id = ?",
            BigDecimal.class, checkingId);

        assertThat(balance).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("spend joins the target in force that month, and a later target does not leak back")
    void spendVsTarget() {
        jdbc.update("""
            INSERT INTO target (user_id, category_id, ledger_entity_id, amount, effective_from, effective_to)
            VALUES (?, ?, ?, 800.00, '2026-01-01'::date, '2026-08-01'::date)
            """, userId, groceriesId, personalId);
        jdbc.update("""
            INSERT INTO target (user_id, category_id, ledger_entity_id, amount, effective_from)
            VALUES (?, ?, ?, 650.00, '2026-08-01'::date)
            """, userId, groceriesId, personalId);

        txn(cardId, "2026-07-10", "700.00", "debit", "July groceries", groceriesId, "k1");
        txn(cardId, "2026-08-10", "700.00", "debit", "August groceries", groceriesId, "k2");

        Map<String, Object> july = jdbc.queryForMap("""
            SELECT target_amount, remaining FROM v_spend_vs_target
            WHERE category_id = ? AND month = '2026-07-01'::date
            """, groceriesId);
        Map<String, Object> august = jdbc.queryForMap("""
            SELECT target_amount, remaining FROM v_spend_vs_target
            WHERE category_id = ? AND month = '2026-08-01'::date
            """, groceriesId);

        // July is governed by the old target and is under; August by the new one and is over.
        assertThat((BigDecimal) july.get("target_amount")).isEqualByComparingTo("800.00");
        assertThat((BigDecimal) july.get("remaining")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) august.get("target_amount")).isEqualByComparingTo("650.00");
        assertThat((BigDecimal) august.get("remaining")).isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("a category with no target reports spend and a null target, not a missing row")
    void spendWithoutTarget() {
        txn(cardId, "2026-08-10", "42.00", "debit", "Groceries", groceriesId, "k1");

        Map<String, Object> row = jdbc.queryForMap("""
            SELECT net_amount, target_amount, remaining FROM v_spend_vs_target
            WHERE category_id = ? AND month = '2026-08-01'::date
            """, groceriesId);

        assertThat((BigDecimal) row.get("net_amount")).isEqualByComparingTo("42.00");
        assertThat(row.get("target_amount")).isNull();
        assertThat(row.get("remaining")).isNull();
    }

    @Test
    @DisplayName("a transaction can be attributed to the business even on a personal account")
    void perTransactionEntityOverride() {
        txn(cardId, "2026-08-10", "200.00", "debit", "Client lunch", groceriesId, "k1");
        jdbc.update("UPDATE transaction SET ledger_entity_id = ? WHERE dedupe_key = 'k1'", businessId);

        Long entity = jdbc.queryForObject("""
            SELECT ledger_entity_id FROM v_monthly_category_spend WHERE category_id = ?
            """, Long.class, groceriesId);

        // The account is Personal; the override wins. This is the tax-time case.
        assertThat(entity).isEqualTo(businessId);
    }

    @Test
    @DisplayName("running balance is ordered by date, and survives an out-of-order insert")
    void runningBalanceSurvivesBackdatedInsert() {
        txn(checkingId, "2026-08-10", "100.00", "credit", "Second", null, "k2");
        // Inserted after, but dated before. The abandoned legacy accountTracker table corrupted
        // every later balance in exactly this situation; a window function does not.
        txn(checkingId, "2026-08-01", "50.00", "credit", "First", null, "k1");

        var rows = jdbc.queryForList("""
            SELECT description, running_balance FROM v_running_balance
            WHERE account_id = ? ORDER BY transaction_date
            """, checkingId);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("description", "First");
        assertThat((BigDecimal) rows.get(0).get("running_balance")).isEqualByComparingTo("50.00");
        assertThat((BigDecimal) rows.get(1).get("running_balance")).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("reconciliation reports the gap between the ledger and the statement, in dollars")
    void reconciliationDetectsAMissingTransaction() {
        txn(checkingId, "2026-08-01", "1000.00", "credit", "Paycheck", null, "k1");
        txn(checkingId, "2026-08-15", "200.00", "debit", "Rent", null, "k2");

        // The bank says 750; the ledger computes 800. A $50 transaction is missing.
        jdbc.update("""
            INSERT INTO statement (user_id, account_id, period_start, period_end, closing_balance)
            VALUES (?, ?, '2026-08-01'::date, '2026-08-31'::date, 750.00)
            """, userId, checkingId);

        Map<String, Object> row = jdbc.queryForMap(
            "SELECT computed_balance, difference FROM v_statement_reconciliation WHERE account_id = ?",
            checkingId);

        assertThat((BigDecimal) row.get("computed_balance")).isEqualByComparingTo("800.00");
        assertThat((BigDecimal) row.get("difference")).isEqualByComparingTo("-50.00");
    }

    @Test
    @DisplayName("a reconciled statement reports a zero difference")
    void reconciliationBalances() {
        txn(checkingId, "2026-08-01", "1000.00", "credit", "Paycheck", null, "k1");
        txn(checkingId, "2026-08-15", "200.00", "debit", "Rent", null, "k2");
        jdbc.update("""
            INSERT INTO statement (user_id, account_id, period_start, period_end, closing_balance)
            VALUES (?, ?, '2026-08-01'::date, '2026-08-31'::date, 800.00)
            """, userId, checkingId);

        BigDecimal difference = jdbc.queryForObject(
            "SELECT difference FROM v_statement_reconciliation WHERE account_id = ?",
            BigDecimal.class, checkingId);

        assertThat(difference).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("net worth separates the business from the personal side")
    void netWorthPerEntity() {
        long ffAccount = insert("INSERT INTO account (user_id, ledger_entity_id, name, account_type) "
            + "VALUES (" + userId + ", " + businessId + ", 'FF Checking', 'checking') RETURNING id");
        txn(checkingId, "2026-08-01", "1000.00", "credit", "Paycheck", null, "k1");
        txn(ffAccount, "2026-08-01", "5000.00", "credit", "Client payment", null, "k2");

        BigDecimal personal = jdbc.queryForObject(
            "SELECT net_worth FROM v_net_worth WHERE ledger_entity_id = ?", BigDecimal.class, personalId);
        BigDecimal business = jdbc.queryForObject(
            "SELECT net_worth FROM v_net_worth WHERE ledger_entity_id = ?", BigDecimal.class, businessId);
        BigDecimal combined = jdbc.queryForObject(
            "SELECT net_worth FROM v_net_worth WHERE user_id = ? AND ledger_entity_id IS NULL",
            BigDecimal.class, userId);

        assertThat(personal).isEqualByComparingTo("1000.00");
        assertThat(business).isEqualByComparingTo("5000.00");
        assertThat(combined).isEqualByComparingTo("6000.00");
    }

    @Test
    @DisplayName("LocalDate boundaries: a purchase on the 1st lands in that month, not the previous")
    void monthBoundary() {
        txn(cardId, "2026-08-01", "10.00", "debit", "First of month", groceriesId, "k1");
        txn(cardId, "2026-07-31", "20.00", "debit", "Last of previous", groceriesId, "k2");

        BigDecimal august = jdbc.queryForObject("""
            SELECT net_amount FROM v_monthly_category_spend
            WHERE category_id = ? AND month = '2026-08-01'::date
            """, BigDecimal.class, groceriesId);
        BigDecimal july = jdbc.queryForObject("""
            SELECT net_amount FROM v_monthly_category_spend
            WHERE category_id = ? AND month = '2026-07-01'::date
            """, BigDecimal.class, groceriesId);

        assertThat(august).isEqualByComparingTo("10.00");
        assertThat(july).isEqualByComparingTo("20.00");
    }
}
