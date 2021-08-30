package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class AccountBalanceSheetDao {

    private static final String CLASS_NAME = "AccountBalanceSheetDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //Get expense item by period and acct type
    //User may only access expense items assigned to the user
    public List<AccountBalanceSheetModel> getExpItemByPeriodNAcctType(int acctId, int periodId, int usersId){

        final String methodName = "getExpItemByPeriodNAcctType() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"expenseItem\".id, \"expenseItem\".\"transactionDate\", \n" +
                "\"account\".name AS \"accountName\" , \"period\".id AS \"periodId\", \n" +
                "\"expenseItem\".name AS \"expenseItemName\", \"account\".id AS \"accountId\", \n" +
                "\"expenseItem\".amount FROM \"expenseItem\"\n" +
                "JOIN \"account\" ON \"expenseItem\".\"account_id\" = \"account\".id\n" +
                "JOIN \"budget_expenseCategory\" ON \"expenseItem\".\"budget_expenseCategory_id\" = \"budget_expenseCategory\".id\n" +
                "JOIN \"budget\" ON \"budget_expenseCategory\".\"budget_id\" = \"budget\".id\n" +
                "JOIN \"period\" ON \"budget\".period_id = \"period\".id\n" +
                "JOIN \"accountPeriod\" ON \"period\".id = \"accountPeriod\".period_id\n" +
                "WHERE \"expenseItem\".\"transactionDate\" >= \"period\".\"startDate\" \n" +
                "AND \"expenseItem\".\"transactionDate\" <= \"period\".\"endDate\"\n" +
                "AND \"account\".id = ?\n" +
                "AND \"period\".id = ?\n" +
                "AND \"period\".\"users_id\" = ?\n" +
                "ORDER BY \"expenseItem\".\"transactionDate\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, periodId, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<AccountBalanceSheetModel> result = new ArrayList<AccountBalanceSheetModel>();
        for(Map<String, Object> row:rows){
            AccountBalanceSheetModel expItem = new AccountBalanceSheetModel();
            expItem.setExpenseItemId((int)row.get("id"));
            expItem.setTransactionDate((Timestamp)row.get("transactionDate"));
            expItem.setAccountName((String)row.get("accountName"));
            expItem.setPeriodId((int)row.get("periodId"));
            expItem.setExpenseItemName((String)row.get("expenseItemName"));
            expItem.setAccountId((int)row.get("accountId"));
            expItem.setAmount((BigDecimal)row.get("amount"));

            result.add(expItem);

        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Get expense item by date range and acct type
    //User may only access expense items assigned to the user
    public List<AccountBalanceSheetModel> getExpItemByDatesNAcctType(
            int acctId, Timestamp startDate, Timestamp dayAfterEndDate, final int usersId){

        final String methodName = "getExpItemByDatesNAcctType() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"expenseItem\".id, \"expenseItem\".\"transactionDate\", \n" +
                "\"account\".name AS \"accountName\" , \"period\".id AS \"periodId\", \n" +
                "\"expenseItem\".name AS \"expenseItemName\", \"account\".id AS \"accountId\", \n" +
                "\"expenseItem\".amount FROM \"expenseItem\"\n" +
                "JOIN \"account\" ON \"expenseItem\".\"account_id\" = \"account\".id\n" +
                "JOIN \"budget_expenseCategory\" ON \"expenseItem\".\"budget_expenseCategory_id\" = \"budget_expenseCategory\".id\n" +
                "JOIN \"budget\" ON \"budget_expenseCategory\".\"budget_id\" = \"budget\".id\n" +
                "JOIN \"period\" ON \"budget\".period_id = \"period\".id\n" +
                "JOIN \"accountPeriod\" ON \"period\".id = \"accountPeriod\".period_id\n" +
                "WHERE \"expenseItem\".\"transactionDate\" >= \"period\".\"startDate\" \n" +
                "AND \"expenseItem\".\"transactionDate\" <= \"period\".\"endDate\"\n" +
                "AND \"account\".id = ?\n" +
                "AND \"expenseItem\".\"transactionDate\" >= ?\n" +
                "AND \"expenseItem\".\"transactionDate\" < ?\n" +
                "AND \"expenseItem\".\"users_id\" = ?\n" +
                "ORDER BY \"expenseItem\".\"transactionDate\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, startDate, dayAfterEndDate, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<AccountBalanceSheetModel> result = new ArrayList<AccountBalanceSheetModel>();
        for(Map<String, Object> row:rows){
            AccountBalanceSheetModel expItem = new AccountBalanceSheetModel();
            expItem.setExpenseItemId((int)row.get("id"));
            expItem.setTransactionDate((Timestamp)row.get("transactionDate"));
            expItem.setAccountName((String)row.get("accountName"));
            expItem.setPeriodId((int)row.get("periodId"));
            expItem.setExpenseItemName((String)row.get("expenseItemName"));
            expItem.setAccountId((int)row.get("accountId"));
            expItem.setAmount((BigDecimal)row.get("amount"));

            result.add(expItem);

        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Get the startDate etc of the oldest unclosed period
    //User may only access periods assigned to the user
    public OldestUnclosedPeriodModel getLastUnclosedPeriod(
            Timestamp targetPeriod, final int usersId) throws EmptyResultDataAccessException {

        final String methodName = "getLastUnclosedPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"period\".\"id\" as \"periodId\", \"period\".\"name\" AS \"periodName\",\n" +
                "\"period\".\"startDate\" AS \"startDate\", \"period\".\"endDate\" AS \"endDate\",\n" +
                "\"period\".\"users_id\" AS \"usersId\", \"budget\".id AS \"budgetId\",\n" +
                "\"budget\".name AS \"budgetName\", \"budget\".\"isClosed\" FROM \"period\" \n" +
                "JOIN \"budget\" ON \"period\".id = \"budget\".period_id\n" +
                "WHERE \"period\".\"startDate\" < ?\n" +
                "AND \"budget\".\"isClosed\" = 'false'\n" +
                "AND \"budget\".\"users_id\" = ?\n" +
                "ORDER BY \"period\".\"startDate\" ASC\n" +
                "LIMIT 1;";
        OldestUnclosedPeriodModel oldestUnclosedPeriod = jdbcTemplate.queryForObject(
                sql, new Object[]{targetPeriod, usersId}, new OldestUnclosedPeriodRowMapper());

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return oldestUnclosedPeriod;
    }

    //Get income items by date range and acct type
    //User may only access income items assigned to the user
    public List<AccountBalanceSheetModel> getIncomeItemByDatesNAcctType(
            int acctId, Timestamp startDate, Timestamp dayAfterEndDate, final int usersId){

        final String methodName = "getIncomeItemByDatesNAcctType() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"incomeItem\".id, \"incomeItem\".\"receivedDate\", \n" +
                "\"account\".name AS \"accountName\" , \"period\".id AS \"periodId\", \n" +
                "\"incomeItem\".name AS \"incomeItemName\", \"account\".id AS \"accountId\", \n" +
                "\"incomeItem\".\"amountReceived\" FROM \"incomeItem\"\n" +
                "JOIN \"account\" ON \"incomeItem\".\"account_id\" = \"account\".id\n" +
                "JOIN \"budget_incomeCategory\" ON \"incomeItem\".\"budget_incomeCategory_id\" = \"budget_incomeCategory\".id\n" +
                "JOIN \"budget\" ON \"budget_incomeCategory\".\"budget_id\" = \"budget\".id\n" +
                "JOIN \"period\" ON \"budget\".period_id = \"period\".id\n" +
                "JOIN \"accountPeriod\" ON \"period\".id = \"accountPeriod\".period_id\n" +
                "WHERE \"incomeItem\".\"receivedDate\" >= \"period\".\"startDate\" \n" +
                "AND \"incomeItem\".\"receivedDate\" <= \"period\".\"endDate\"\n" +
                "AND \"account\".id =?\n" +
                "AND \"incomeItem\".\"receivedDate\" >= ?\n" +
                "AND \"incomeItem\".\"receivedDate\" < ?\n" +
                "AND \"incomeItem\".\"users_id\" = ?\n" +
                "ORDER BY \"incomeItem\".\"receivedDate\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, startDate, dayAfterEndDate, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<AccountBalanceSheetModel> result = new ArrayList<AccountBalanceSheetModel>();
        for(Map<String, Object> row:rows){
            AccountBalanceSheetModel incomeItem = new AccountBalanceSheetModel();
            incomeItem.setIncomeItemId((int)row.get("id"));
            incomeItem.setTransactionDate((Timestamp)row.get("receivedDate"));
            incomeItem.setAccountName((String)row.get("accountName"));
            incomeItem.setPeriodId((int)row.get("periodId"));
            incomeItem.setIncomeItemName((String)row.get("incomeItemName"));
            incomeItem.setAccountId((int)row.get("accountId"));
            incomeItem.setAmount((BigDecimal)row.get("amountReceived"));

            result.add(incomeItem);

        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Get income item by period and acct type
    //User may only access income items assigned to the user
    public List<AccountBalanceSheetModel> getIncomeItemByPeriodNAcctType(int acctId, int periodId, final int usersId){

        final String methodName = "getIncomeItemByPeriodNAcctType() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"incomeItem\".id, \"incomeItem\".\"receivedDate\", \n" +
                "\"account\".name AS \"accountName\" , \"period\".id AS \"periodId\", \n" +
                "\"incomeItem\".name AS \"incomeItemName\", \"account\".id AS \"accountId\", \n" +
                "\"incomeItem\".\"amountReceived\" FROM \"incomeItem\"\n" +
                "JOIN \"account\" ON \"incomeItem\".\"account_id\" = \"account\".id\n" +
                "JOIN \"budget_incomeCategory\" ON \"incomeItem\".\"budget_incomeCategory_id\" = \"budget_incomeCategory\".id\n" +
                "JOIN \"budget\" ON \"budget_incomeCategory\".\"budget_id\" = \"budget\".id\n" +
                "JOIN \"period\" ON \"budget\".period_id = \"period\".id\n" +
                "JOIN \"accountPeriod\" ON \"period\".id = \"accountPeriod\".period_id\n" +
                "WHERE \"incomeItem\".\"receivedDate\" >= \"period\".\"startDate\" \n" +
                "AND \"incomeItem\".\"receivedDate\" <= \"period\".\"endDate\"\n" +
                "AND \"account\".id = ?\n" +
                "AND \"period\".id =  ?\n" +
                "AND \"period\".\"users_id\" = ?\n" +
                "ORDER BY \"incomeItem\".\"receivedDate\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, periodId, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<AccountBalanceSheetModel> result = new ArrayList<AccountBalanceSheetModel>();
        for(Map<String, Object> row:rows){
            AccountBalanceSheetModel incomeItem = new AccountBalanceSheetModel();
            incomeItem.setIncomeItemId((int)row.get("id"));
            incomeItem.setTransactionDate((Timestamp)row.get("receivedDate"));
            incomeItem.setAccountName((String)row.get("accountName"));
            incomeItem.setPeriodId((int)row.get("periodId"));
            incomeItem.setIncomeItemName((String)row.get("incomeItemName"));
            incomeItem.setAccountId((int)row.get("accountId"));
            incomeItem.setAmount((BigDecimal)row.get("amountReceived"));

            result.add(incomeItem);

        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

}
