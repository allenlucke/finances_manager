package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

import com.Allen.SpringFinancesServer.ExpenseCategory.ExpenseCategoryModel;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BudgetExpenseCategoryRowMapper implements RowMapper<BudgetExpenseCategoryModel> {

    @Override
    public BudgetExpenseCategoryModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        BudgetExpenseCategoryModel budgExpCat = new BudgetExpenseCategoryModel();

        budgExpCat.setId(rs.getInt("id"));
        budgExpCat.setBudgetId(rs.getInt("budget_id"));
        budgExpCat.setExpenseCategoryId(rs.getInt("expenseCategory_id"));
        budgExpCat.setAmountBudgeted(rs.getBigDecimal("amountBudgeted"));

        return budgExpCat;
    }
}
