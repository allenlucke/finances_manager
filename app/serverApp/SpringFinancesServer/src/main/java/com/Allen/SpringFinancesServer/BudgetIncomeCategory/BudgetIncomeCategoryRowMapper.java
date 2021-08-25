package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryModel;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BudgetIncomeCategoryRowMapper implements RowMapper<BudgetIncomeCategoryModel> {

    @Override
    public BudgetIncomeCategoryModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();

        budgIncomeCat.setId(rs.getInt("id"));
        budgIncomeCat.setBudgetId(rs.getInt("budget_id"));
        budgIncomeCat.setIncomeCategoryId(rs.getInt("incomeCategory_id"));
        budgIncomeCat.setAmountBudgeted(rs.getBigDecimal("amountBudgeted"));
        budgIncomeCat.setUsersId(rs.getInt("users_id"));

        return budgIncomeCat;
    }
}
