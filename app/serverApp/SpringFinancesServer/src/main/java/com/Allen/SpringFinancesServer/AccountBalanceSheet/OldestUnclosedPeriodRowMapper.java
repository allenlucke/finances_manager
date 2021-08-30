package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class OldestUnclosedPeriodRowMapper implements RowMapper<OldestUnclosedPeriodModel> {

    private static final String CLASS_NAME = "OldestUnclosedPeriodRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public OldestUnclosedPeriodModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        OldestUnclosedPeriodModel unclosedPer = new OldestUnclosedPeriodModel();

        unclosedPer.setPeriodId(rs.getInt("periodId"));
        unclosedPer.setPeriodName(rs.getString("periodName"));
        unclosedPer.setStartDate(rs.getTimestamp("startDate"));
        unclosedPer.setEndDate(rs.getTimestamp("endDate"));
        unclosedPer.setUsersId(rs.getInt("usersId"));
        unclosedPer.setBudgetId(rs.getInt("budgetId"));
        unclosedPer.setBudgetName(rs.getString("budgetName"));
        unclosedPer.setClosed(rs.getBoolean("isClosed"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return unclosedPer;
    }
}
