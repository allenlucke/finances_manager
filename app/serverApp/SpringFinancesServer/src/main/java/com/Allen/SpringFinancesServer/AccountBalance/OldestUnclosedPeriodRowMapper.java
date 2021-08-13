package com.Allen.SpringFinancesServer.AccountBalance;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class OldestUnclosedPeriodRowMapper implements RowMapper<OldestUnclosedPeriodModel> {

    @Override
    public OldestUnclosedPeriodModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        OldestUnclosedPeriodModel unclosedPer = new OldestUnclosedPeriodModel();

        unclosedPer.setPeriodId(rs.getInt("periodId"));
        unclosedPer.setPeriodName(rs.getString("periodName"));
        unclosedPer.setStartDate(rs.getTimestamp("startDate"));
        unclosedPer.setEndDate(rs.getTimestamp("endDate"));
        unclosedPer.setUsersId(rs.getInt("usersId"));
        unclosedPer.setBudgetId(rs.getInt("budgetId"));
        unclosedPer.setBudgetName(rs.getString("budgetName"));
        unclosedPer.setClosed(rs.getBoolean("isClosed"));

        return unclosedPer;
    }
}
