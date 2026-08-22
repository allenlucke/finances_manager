package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class BudgetExpenseCategoryRowMapper implements RowMapper<BudgetExpenseCategoryModel> {

    private static final String CLASS_NAME = "BudgetExpenseCategoryRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public BudgetExpenseCategoryModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        BudgetExpenseCategoryModel budgExpCat = new BudgetExpenseCategoryModel();

        budgExpCat.setId(rs.getInt("id"));
        budgExpCat.setBudgetId(rs.getInt("budget_id"));
        budgExpCat.setExpenseCategoryId(rs.getInt("expenseCategory_id"));
        budgExpCat.setAmountBudgeted(rs.getBigDecimal("amountBudgeted"));
        budgExpCat.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return budgExpCat;
    }
}
