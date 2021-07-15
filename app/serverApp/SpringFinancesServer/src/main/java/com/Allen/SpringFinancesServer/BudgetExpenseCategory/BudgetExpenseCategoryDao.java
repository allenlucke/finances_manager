package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
}
