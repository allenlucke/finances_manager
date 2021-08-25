package com.Allen.SpringFinancesServer.IncomeItem;

import com.Allen.SpringFinancesServer.Period.PeriodModel;
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
public class IncomeItemDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<IncomeItemModel> adminGetAllIncomeItems() {
        String sql = "SELECT * FROM \"incomeItem\";";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<IncomeItemModel> result = new ArrayList<IncomeItemModel>();
        for(Map<String, Object> row:rows){
            IncomeItemModel incomeItem = new IncomeItemModel();
            incomeItem.setId((int)row.get("id"));
            incomeItem.setBudgetIncomeCategoryId((int)row.get("budget_incomeCategory_id"));
            incomeItem.setName((String)row.get("name"));
            incomeItem.setReceivedDate((Timestamp)row.get("receivedDate"));
            incomeItem.setAmountExpected((BigDecimal)row.get("amountExpected"));
            incomeItem.setAmountReceived((BigDecimal)row.get("amountReceived"));
            incomeItem.setAccountId((int)row.get("account_id"));
            incomeItem.setUsersId((int)row.get("users_id"));

            result.add(incomeItem);
        }
        return result;
    }


    public List<IncomeItemModel> getAllIncomeItems(final int usersId) {
        String sql = "SELECT * FROM \"incomeItem\" WHERE \"users_id\" = ?;";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        List<IncomeItemModel> result = new ArrayList<IncomeItemModel>();
        for(Map<String, Object> row:rows){
            IncomeItemModel incomeItem = new IncomeItemModel();
            incomeItem.setId((int)row.get("id"));
            incomeItem.setBudgetIncomeCategoryId((int)row.get("budget_incomeCategory_id"));
            incomeItem.setName((String)row.get("name"));
            incomeItem.setReceivedDate((Timestamp)row.get("receivedDate"));
            incomeItem.setAmountExpected((BigDecimal)row.get("amountExpected"));
            incomeItem.setAmountReceived((BigDecimal)row.get("amountReceived"));
            incomeItem.setAccountId((int)row.get("account_id"));
            incomeItem.setUsersId((int)row.get("users_id"));

            result.add(incomeItem);
        }
        return result;
    }

    public List<IncomeItemModel> getIncomeItemById(final int itemId, final int usersId){
        String sql = "SELECT * FROM \"incomeItem\" WHERE \"id\" = ? AND \"users_id\" = ?;";

        IncomeItemModel incomeItem = jdbcTemplate.queryForObject( sql, new Object[]{itemId, usersId}, new IncomeItemRowMapper());

        List<IncomeItemModel> result = new ArrayList<IncomeItemModel>();

        result.add(incomeItem);

        return result;
    }

    public List<IncomeItemModel> adminGetIncomeItemById(final int itemId){
        String sql = "SELECT * FROM \"incomeItem\" WHERE \"id\" = ?;";

        IncomeItemModel incomeItem = jdbcTemplate.queryForObject( sql, new Object[]{itemId}, new IncomeItemRowMapper());

        List<IncomeItemModel> result = new ArrayList<IncomeItemModel>();

        result.add(incomeItem);

        return result;
    }

    public List<ReturnIdModel> addIncomeItemReturnId(final IncomeItemModel incomeItem) {
        String sql = "INSERT INTO \"incomeItem\"\n" +
                "\t(\"budget_incomeCategory_id\", \"name\", \"receivedDate\", \n" +
                "\t \"amountExpected\", \"amountReceived\",\"account_id\", \"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?, ?, ?, ?, ?, ?) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, incomeItem.getBudgetIncomeCategoryId());
            ps.setString(2,incomeItem.getName());
            ps.setTimestamp(3, incomeItem.getReceivedDate());
            ps.setBigDecimal(4, incomeItem.getAmountExpected());
            ps.setBigDecimal(5, incomeItem.getAmountReceived());
            ps.setInt(6, incomeItem.getAccountId());
            ps.setInt(7, incomeItem.getUsersId());

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
