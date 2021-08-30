package com.Allen.SpringFinancesServer.Budget;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class BudgetRowMapper implements RowMapper<BudgetModel> {

    private static final String CLASS_NAME = "BudgetRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public BudgetModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        BudgetModel budget = new BudgetModel();

        budget.setId(rs.getInt("id"));
        budget.setName(rs.getString("name"));
        budget.setPeriodId(rs.getInt("period_id"));
        budget.setClosed(rs.getBoolean("isClosed"));
        budget.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return budget;
    }
}