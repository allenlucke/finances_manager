package com.Allen.SpringFinancesServer.AccountBalance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AccountBalanceDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //Get expense item by period and acct type
    public List<BalanceSheetModel> getExpItemByPeriodNAcctType(int acctId, int periodId){
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
                "ORDER BY \"expenseItem\".\"transactionDate\";";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, periodId} ); /*,
                new ExpItemMapper());*/
        List<BalanceSheetModel> result = new ArrayList<BalanceSheetModel>();
        for(Map<String, Object> row:rows){
            BalanceSheetModel expItem = new BalanceSheetModel();


            expItem.setExpenseItemId((int)row.get("id"));
            expItem.setTransactionDate((Timestamp)row.get("transactionDate"));
            expItem.setAccountName((String)row.get("accountName"));
            expItem.setPeriodId((int)row.get("periodId"));
            expItem.setExpenseItemName((String)row.get("expenseItemName"));
            expItem.setAccountId((int)row.get("accountId"));
            expItem.setAmount((BigDecimal)row.get("amount"));

            result.add(expItem);

        }
        return result;
    }

    //Get expense item by date range and acct type
    public List<BalanceSheetModel> getExpItemByDatesNAcctType(int acctId, Timestamp startDate, Timestamp dayAfterEndDate){
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
                "ORDER BY \"expenseItem\".\"transactionDate\";";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, startDate, dayAfterEndDate} ); /*,
                new ExpItemMapper());*/
        List<BalanceSheetModel> result = new ArrayList<BalanceSheetModel>();
        for(Map<String, Object> row:rows){
            BalanceSheetModel expItem = new BalanceSheetModel();


            expItem.setExpenseItemId((int)row.get("id"));
            expItem.setTransactionDate((Timestamp)row.get("transactionDate"));
            expItem.setAccountName((String)row.get("accountName"));
            expItem.setPeriodId((int)row.get("periodId"));
            expItem.setExpenseItemName((String)row.get("expenseItemName"));
            expItem.setAccountId((int)row.get("accountId"));
            expItem.setAmount((BigDecimal)row.get("amount"));

            result.add(expItem);

        }
        return result;
    }

    //Get the startDate etc of the oldest unclosed period
    public OldestUnclosedPeriodModel getLastUnclosedPeriod(Timestamp targetPeriod) throws EmptyResultDataAccessException {
        String sql = "SELECT \"period\".\"id\" as \"periodId\", \"period\".\"name\" AS \"periodName\",\n" +
                "\"period\".\"startDate\" AS \"startDate\", \"period\".\"endDate\" AS \"endDate\",\n" +
                "\"period\".\"users_id\" AS \"usersId\", \"budget\".id AS \"budgetId\",\n" +
                "\"budget\".name AS \"budgetName\", \"budget\".\"isClosed\" FROM \"period\" \n" +
                "JOIN \"budget\" ON \"period\".id = \"budget\".period_id\n" +
                "WHERE \"period\".\"startDate\" < ?\n" +
                "AND \"budget\".\"isClosed\" = 'false'\n" +
                "ORDER BY \"period\".\"startDate\" ASC\n" +
                "LIMIT 1;";

        OldestUnclosedPeriodModel oldestUnclosedPeriod = jdbcTemplate.queryForObject( sql, new Object[]{targetPeriod}, new OldestUnclosedPeriodRowMapper());

        return oldestUnclosedPeriod;
    }

    //Get income item by date range and acct type
    public List<BalanceSheetModel> getIncomeItemByDatesNAcctType(int acctId, Timestamp startDate, Timestamp dayAfterEndDate){
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
                "ORDER BY \"incomeItem\".\"receivedDate\";";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, startDate, dayAfterEndDate} );
        List<BalanceSheetModel> result = new ArrayList<BalanceSheetModel>();
        for(Map<String, Object> row:rows){
            BalanceSheetModel incomeItem = new BalanceSheetModel();

            incomeItem.setIncomeItemId((int)row.get("id"));
            incomeItem.setTransactionDate((Timestamp)row.get("receivedDate"));
            incomeItem.setAccountName((String)row.get("accountName"));
            incomeItem.setPeriodId((int)row.get("periodId"));
            incomeItem.setIncomeItemName((String)row.get("incomeItemName"));
            incomeItem.setAccountId((int)row.get("accountId"));
            incomeItem.setAmount((BigDecimal)row.get("amount"));

            result.add(incomeItem);

        }
        return result;
    }

    //Get income item by period and acct type
    public List<BalanceSheetModel> getIncomeItemByPeriodNAcctType(int acctId, int periodId){
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
                "ORDER BY \"incomeItem\".\"receivedDate\";";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, periodId} ); /*,
                new ExpItemMapper());*/
        List<BalanceSheetModel> result = new ArrayList<BalanceSheetModel>();
        for(Map<String, Object> row:rows){
            BalanceSheetModel incomeItem = new BalanceSheetModel();


            incomeItem.setIncomeItemId((int)row.get("id"));
            incomeItem.setTransactionDate((Timestamp)row.get("receivedDate"));
            incomeItem.setAccountName((String)row.get("accountName"));
            incomeItem.setPeriodId((int)row.get("periodId"));
            incomeItem.setIncomeItemName((String)row.get("incomeItemName"));
            incomeItem.setAccountId((int)row.get("accountId"));
            incomeItem.setAmount((BigDecimal)row.get("amountReceived"));

            result.add(incomeItem);

        }
        return result;
    }

}
