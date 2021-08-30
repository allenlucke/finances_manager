package com.Allen.SpringFinancesServer.ExpenseItem;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class ExpenseItemDao {

    private static final String CLASS_NAME = "ExpenseItemDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //User may only access expense items assigned to the user
    public List<ExpenseItemModel> getAllExpItems(final int usersId) {

        final String methodName = "getAllExpItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseItem\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<ExpenseItemModel> result = new ArrayList<ExpenseItemModel>();
        for(Map<String, Object> row:rows){
            ExpenseItemModel expItem = new ExpenseItemModel();
            expItem.setId((int)row.get("id"));
            expItem.setBudgetExpenseCategoryId((int)row.get("budget_expenseCategory_id"));
            expItem.setName((String)row.get("name"));
            expItem.setTransactionDate((Timestamp)row.get("transactionDate"));
            expItem.setAmount((BigDecimal)row.get("amount"));
            expItem.setPaidWithCredit((Boolean)row.get("paidWithCredit"));
            expItem.setPaymentToCreditAccount((Boolean)row.get("paymentToCreditAccount"));
            expItem.setInterestPaymentToCreditAccount((Boolean)row.get("interestPaymentToCreditAccount"));
            expItem.setAccountId((int)row.get("account_id"));
            expItem.setUsersId((int)row.get("users_id"));

            result.add(expItem);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all expense items
    public List<ExpenseItemModel> adminGetAllExpItems() {

        final String methodName = "adminGetAllExpItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseItem\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<ExpenseItemModel> result = new ArrayList<ExpenseItemModel>();
        for(Map<String, Object> row:rows){
            ExpenseItemModel expItem = new ExpenseItemModel();
            expItem.setId((int)row.get("id"));
            expItem.setBudgetExpenseCategoryId((int)row.get("budget_expenseCategory_id"));
            expItem.setName((String)row.get("name"));
            expItem.setTransactionDate((Timestamp)row.get("transactionDate"));
            expItem.setAmount((BigDecimal)row.get("amount"));
            expItem.setPaidWithCredit((Boolean)row.get("paidWithCredit"));
            expItem.setPaymentToCreditAccount((Boolean)row.get("paymentToCreditAccount"));
            expItem.setInterestPaymentToCreditAccount((Boolean)row.get("interestPaymentToCreditAccount"));
            expItem.setAccountId((int)row.get("account_id"));
            expItem.setUsersId((int)row.get("users_id"));

            result.add(expItem);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access expense items assigned to the user
    public List<ExpenseItemModel> getExpItemById(final int itemId, final int usersId){

        final String methodName = "getExpItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseItem\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        ExpenseItemModel expenseItem = jdbcTemplate.queryForObject(
                sql, new Object[]{itemId, usersId}, new ExpenseItemRowMapper());

        List<ExpenseItemModel> result = new ArrayList<ExpenseItemModel>();
        result.add(expenseItem);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any expense item
    public List<ExpenseItemModel> adminGetExpItemById(int id){

        final String methodName = "adminGetExpItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseItem\" WHERE \"id\" = ?;";
        ExpenseItemModel expenseItem = jdbcTemplate.queryForObject( sql, new Object[]{id}, new ExpenseItemRowMapper());

        List<ExpenseItemModel> result = new ArrayList<ExpenseItemModel>();
        result.add(expenseItem);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access expense items assigned to the user
    public List<ExpenseItemModel> getExpItemByPeriod(int periodId, final int usersId){

        final String methodName = "getExpItemByPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"expenseItem\".id, \"expenseItem\".\"budget_expenseCategory_id\", \"expenseItem\".name,\n" +
                "\"expenseItem\".\"transactionDate\", \"expenseItem\".\"amount\", \"expenseItem\".\"paidWithCredit\",\n" +
                "\"expenseItem\".\"paymentToCreditAccount\", \"expenseItem\".\"interestPaymentToCreditAccount\",\n" +
                "\"expenseItem\".\"account_id\", \"expenseItem\".\"users_id\" FROM \"expenseItem\"\n" +
                "JOIN \"budget_expenseCategory\" ON \"expenseItem\".\"budget_expenseCategory_id\" = \"budget_expenseCategory\".id\n" +
                "JOIN \"budget\" ON \"budget_expenseCategory\".\"budget_id\" = \"budget\".id\n" +
                "WHERE \"budget\".\"period_id\" = ?\n" +
                "AND \"budget\".\"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {periodId, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<ExpenseItemModel> result = new ArrayList<ExpenseItemModel>();
        for(Map<String, Object> row:rows){
            ExpenseItemModel expItem = new ExpenseItemModel();
            expItem.setId((int)row.get("id"));
            expItem.setBudgetExpenseCategoryId((int)row.get("budget_expenseCategory_id"));
            expItem.setName((String)row.get("name"));
            expItem.setTransactionDate((Timestamp)row.get("transactionDate"));
            expItem.setAmount((BigDecimal)row.get("amount"));
            expItem.setPaidWithCredit((Boolean)row.get("paidWithCredit"));
            expItem.setPaymentToCreditAccount((Boolean)row.get("paymentToCreditAccount"));
            expItem.setInterestPaymentToCreditAccount((Boolean)row.get("interestPaymentToCreditAccount"));
            expItem.setAccountId((int)row.get("account_id"));
            expItem.setUsersId((int)row.get("users_id"));

            result.add(expItem);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addExpItemReturnId(final ExpenseItemModel expenseItem) {

        final String methodName = "addExpItemReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"expenseItem\"\n" +
                "\t(\"budget_expenseCategory_id\", \"name\", \"transactionDate\", \n" +
                "\t \"amount\", \"paidWithCredit\", \"paymentToCreditAccount\", \n" +
                "\t \"interestPaymentToCreditAccount\", \"account_id\", \"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, expenseItem.getBudgetExpenseCategoryId());
            ps.setString(2, expenseItem.getName());
            ps.setTimestamp(3, expenseItem.getTransactionDate());
            ps.setBigDecimal(4, expenseItem.getAmount());
            ps.setBoolean(5, expenseItem.getPaidWithCredit());
            ps.setBoolean(6, expenseItem.getPaymentToCreditAccount());
            ps.setBoolean(7, expenseItem.getInterestPaymentToCreditAccount());
            ps.setInt(8, expenseItem.getAccountId());
            ps.setInt(9, expenseItem.getUsersId());

            return ps;
        },holder);
        int id = (int) holder.getKey();

        List<ReturnIdModel> result = new ArrayList<ReturnIdModel>();
        ReturnIdModel idObject = new ReturnIdModel();
        idObject.setId(id);

        result.add(idObject);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }
}
