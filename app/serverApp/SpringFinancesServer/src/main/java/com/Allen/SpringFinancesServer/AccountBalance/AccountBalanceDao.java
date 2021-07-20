package com.Allen.SpringFinancesServer.AccountBalance;

import com.Allen.SpringFinancesServer.Account.AccountModel;
import org.springframework.beans.factory.annotation.Autowired;
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

    public List<ExpItemModel> getExpItemByPeriodNAcctType(int acctId, int periodId){
        String sql = "SELECT \"expenseItem\".id, \"expenseItem\".\"transactionDate\", \"account\".name AS \"accountName\" , \"period\".id AS \"periodId\", \n" +
                "\"expenseItem\".name AS \"expenseItemName\", \"account\".id AS \"accountId\", \"expenseItem\".amount FROM \"expenseItem\"\n" +
                "JOIN \"account\" ON \"expenseItem\".\"account_id\" = \"account\".id\n" +
                "JOIN \"budget_expenseCategory\" ON \"expenseItem\".\"budget_expenseCategory_id\" = \"budget_expenseCategory\".id\n" +
                "JOIN \"budget\" ON \"budget_expenseCategory\".id = \"budget\".id\n" +
                "JOIN \"period\" ON \"budget\".period_id = \"period\".id\n" +
                "JOIN \"accountPeriod\" ON \"period\".id = \"accountPeriod\".period_id\n" +
                "WHERE \"expenseItem\".\"transactionDate\" >= \"period\".\"startDate\" AND \"expenseItem\".\"transactionDate\" <= \"period\".\"endDate\"\n" +
                "AND \"account\".id =?\n" +
                "AND \"period\".id = ?\n" +
                "ORDER BY \"expenseItem\".\"transactionDate\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {acctId, periodId} ); /*,
                new ExpItemMapper());*/
        List<ExpItemModel> result = new ArrayList<ExpItemModel>();
        for(Map<String, Object> row:rows){
            ExpItemModel expItem = new ExpItemModel();


            expItem.setId((int)row.get("id"));
            expItem.setTransactionDate((Timestamp)row.get("transactionDate"));
            expItem.setAccountName((String)row.get("accountName"));
            expItem.setPeriodId((int)row.get("periodId"));
            expItem.setExpenseItemName((String)row.get("expenseItemName"));
            expItem.setAccountId((int)row.get("accountId"));
            expItem.setAmount((BigDecimal)row.get("amount"));

            result.add(expItem);

//            expItem.setId(rs.getInt("id"));
//            expItem.setTransactionDate(rs.getTimestamp("transactionDate"));
//            expItem.setAccountName(rs.getString("accountName"));
//            expItem.setPeriodId(rs.getInt("periodId"));
//            expItem.setExpenseItemName(rs.getString("expenseItemName"));
//            expItem.setAccountId(rs.getInt("accountId"));
//            expItem.setAmount(rs.getBigDecimal("amount"));
        }
        return result;
    }

//        List<ExpItemModel> result = new ArrayList<>();
//        for(Map<String, Object> row:rows){
//        return rows;
//    }
//
//    public List<AccountModel> getAllAccounts(){
//        String sql = "SELECT * FROM \"account\";";
//        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
//
//        List<AccountModel> result = new ArrayList<AccountModel>();
//        for(Map<String, Object> row:rows){
//            AccountModel account = new AccountModel();
//            account.setId((int)row.get("id"));
//            account.setName((String)row.get("name"));
//            account.setUsersId((int)row.get("users_id"));
//            account.setCredit((boolean)row.get("isCredit"));
//            account.setActive((boolean)row.get("isActive"));
//
//            result.add(account);
//        }
//        return result;
//    }
}
