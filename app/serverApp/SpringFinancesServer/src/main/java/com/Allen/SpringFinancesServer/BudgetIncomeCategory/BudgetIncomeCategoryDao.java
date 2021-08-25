package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BudgetIncomeCategoryDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<BudgetIncomeCategoryModel> getAllBudgetIncomeCats(final int usersId) {
        String sql = "SELECT * FROM \"budget_incomeCategory\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();
            budgIncomeCat.setId((int)row.get("id"));
            budgIncomeCat.setBudgetId((int)row.get("budget_id"));
            budgIncomeCat.setIncomeCategoryId((int)row.get("incomeCategory_id"));
            budgIncomeCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgIncomeCat.setUsersId((int)row.get("users_id"));

            result.add(budgIncomeCat);
        }
        return result;
    }

    public List<BudgetIncomeCategoryModel> adminGetAllBudgetIncomeCats() {
        String sql = "SELECT * FROM \"budget_incomeCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();
            budgIncomeCat.setId((int)row.get("id"));
            budgIncomeCat.setBudgetId((int)row.get("budget_id"));
            budgIncomeCat.setIncomeCategoryId((int)row.get("incomeCategory_id"));
            budgIncomeCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgIncomeCat.setUsersId((int)row.get("users_id"));

            result.add(budgIncomeCat);
        }
        return result;
    }

    public List<BudgetIncomeCategoryModel> getBudgetIncomeCatById(final int budgIncCatId, final int usersId){
        String sql = "SELECT * FROM \"budget_incomeCategory\" WHERE \"id\" = ? AND \"users_id\" = ?;";

        BudgetIncomeCategoryModel budgetIncomeCat = jdbcTemplate.queryForObject(
                sql, new Object[]{budgIncCatId, usersId}, new BudgetIncomeCategoryRowMapper());

        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();

        result.add(budgetIncomeCat);

        return result;
    }

    public List<BudgetIncomeCategoryModel> adminGetBudgetIncomeCatById(int id){
        String sql = "SELECT * FROM \"budget_incomeCategory\" WHERE \"id\" = ?;";

        BudgetIncomeCategoryModel budgetIncomeCat = jdbcTemplate.queryForObject(
                sql, new Object[]{id}, new BudgetIncomeCategoryRowMapper());

        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();

        result.add(budgetIncomeCat);

        return result;
    }

    public List<ReturnIdModel> addBudgetIncomeCatReturnId(final BudgetIncomeCategoryModel budgetIncomeCat) {
        String sql = "INSERT INTO \"budget_expenseCategory\"\n" +
                "\t(\"budget_id\", \"expenseCategory_id\", \"amountBudgeted\", \"users_id\" )\n" +
                "VALUES\n" +
                "\t(?, ?, ?, ? );";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, budgetIncomeCat.getBudgetId());
            ps.setInt(2, budgetIncomeCat.getIncomeCategoryId());
            ps.setInt(3,budgetIncomeCat.getUsersId());
//            ps.setBigDecimal(3, budgetExpCat.getAmountBudgeted());

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
