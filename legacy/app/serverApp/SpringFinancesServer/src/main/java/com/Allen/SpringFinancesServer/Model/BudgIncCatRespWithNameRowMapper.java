package com.Allen.SpringFinancesServer.Model;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class BudgIncCatRespWithNameRowMapper implements RowMapper<BudgIncCatRespWithName> {

    private static final String CLASS_NAME = "BudgIncCatRespWithNameRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public BudgIncCatRespWithName mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        BudgIncCatRespWithName budgIncomeCat = new BudgIncCatRespWithName();

        budgIncomeCat.setId(rs.getInt("id"));
        budgIncomeCat.setBudgetId(rs.getInt("budget_id"));
        budgIncomeCat.setIncomeCategoryId(rs.getInt("incomeCategory_id"));
        budgIncomeCat.setAmountBudgeted(rs.getBigDecimal("amountBudgeted"));
        budgIncomeCat.setUsersId(rs.getInt("users_id"));
        budgIncomeCat.setName(rs.getString("name"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return budgIncomeCat;
    }
}
