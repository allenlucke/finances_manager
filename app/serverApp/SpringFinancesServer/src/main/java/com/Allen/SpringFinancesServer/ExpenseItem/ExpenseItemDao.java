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

@Service
public class ExpenseItemDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<ExpenseItemModel> getAllExpItems() {
        String sql = "SELECT * FROM \"expenseItem\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

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
        return result;
    }

    public List<ExpenseItemModel> getExpItemById(int id){
        String sql = "SELECT * FROM \"expenseItem\" WHERE \"id\" = ?;";

        ExpenseItemModel expenseItem = jdbcTemplate.queryForObject( sql, new Object[]{id}, new ExpenseItemRowMapper());

        List<ExpenseItemModel> result = new ArrayList<ExpenseItemModel>();

        result.add(expenseItem);

        return result;
    }

    public List<ReturnIdModel> addExpItemReturnId(final ExpenseItemModel expenseItem) {
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

        return result;
    }
}
