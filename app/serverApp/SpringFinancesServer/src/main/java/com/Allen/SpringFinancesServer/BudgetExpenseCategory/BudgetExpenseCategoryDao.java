package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

import com.Allen.SpringFinancesServer.ExpenseItem.ExpenseItemModel;
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
public class BudgetExpenseCategoryDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<BudgetExpenseCategoryModel> getAllBudgetExpCats() {
        String sql = "SELECT * FROM \"budget_expenseCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<BudgetExpenseCategoryModel> result = new ArrayList<BudgetExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetExpenseCategoryModel budgExpCat = new BudgetExpenseCategoryModel();
            budgExpCat.setId((int)row.get("id"));
            budgExpCat.setBudgetId((int)row.get("budget_id"));
            budgExpCat.setExpenseCategoryId((int)row.get("expenseCategory_id"));
            budgExpCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));

            result.add(budgExpCat);
        }
        return result;
    }

    public List<BudgetExpenseCategoryModel> getBudgetExpCatById(int id){
        String sql = "SELECT * FROM \"budget_expenseCategory\" WHERE \"id\" = ?;";

        BudgetExpenseCategoryModel budgetExpCat = jdbcTemplate.queryForObject( sql, new Object[]{id}, new BudgetExpenseCategoryRowMapper());

        List<BudgetExpenseCategoryModel> result = new ArrayList<BudgetExpenseCategoryModel>();

        result.add(budgetExpCat);

        return result;
    }

    public List<ReturnIdModel> addBudgetExpCatReturnId(final BudgetExpenseCategoryModel budgetExpCat) {
        String sql = "INSERT INTO \"budget_expenseCategory\"\n" +
                "\t(\"budget_id\", \"expenseCategory_id\", \"amountBudgeted\" )\n" +
                "VALUES\n" +
                "\t(?, ?, ? ) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, budgetExpCat.getBudgetId());
            ps.setInt(2, budgetExpCat.getExpenseCategoryId());
            ps.setBigDecimal(3, budgetExpCat.getAmountBudgeted());

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
