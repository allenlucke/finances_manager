package com.Allen.SpringFinancesServer.Budget;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class BudgetRowMapper implements RowMapper<BudgetModel> {

    @Override
    public BudgetModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        BudgetModel budget = new BudgetModel();

        budget.setId(rs.getInt("id"));
        budget.setName(rs.getString("name"));
        budget.setPeriodId(rs.getInt("period_id"));
        budget.setClosed(rs.getBoolean("isClosed"));

        return budget;
    }
}