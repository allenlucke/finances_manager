package com.Allen.SpringFinancesServer.IncomeItem;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class IncomeItemRowMapper implements RowMapper<IncomeItemModel> {

    @Override
    public IncomeItemModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        IncomeItemModel incomeItem = new IncomeItemModel();

        incomeItem.setId(rs.getInt("id"));
        incomeItem.setBudgetIncomeCategoryId(rs.getInt("budget_incomeCategory_id"));
        incomeItem.setName(rs.getString("name"));
        incomeItem.setReceivedDate(rs.getTimestamp("receivedDate"));
        incomeItem.setAmountExpected(rs.getBigDecimal("amountExpected"));
        incomeItem.setAmountReceived(rs.getBigDecimal("amountReceived"));
        incomeItem.setAccountId(rs.getInt("account_id"));
        incomeItem.setUsersId(rs.getInt("users_id"));

        return incomeItem;
    }
}
