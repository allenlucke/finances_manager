package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryModel;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class BudgetIncomeCategoryRowMapper implements RowMapper<BudgetIncomeCategoryModel> {

    private static final String CLASS_NAME = "BudgetIncomeCategoryRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public BudgetIncomeCategoryModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();

        budgIncomeCat.setId(rs.getInt("id"));
        budgIncomeCat.setBudgetId(rs.getInt("budget_id"));
        budgIncomeCat.setIncomeCategoryId(rs.getInt("incomeCategory_id"));
        budgIncomeCat.setAmountBudgeted(rs.getBigDecimal("amountBudgeted"));
        budgIncomeCat.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return budgIncomeCat;
    }
}
