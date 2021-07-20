package com.Allen.SpringFinancesServer.AccountBalance;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ExpItemMapper implements RowMapper<ExpItemModel> {

    @Override
    public ExpItemModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        ExpItemModel expItem = new ExpItemModel();

        expItem.setId(rs.getInt("id"));
        expItem.setTransactionDate(rs.getTimestamp("transactionDate"));
        expItem.setAccountName(rs.getString("accountName"));
        expItem.setPeriodId(rs.getInt("periodId"));
        expItem.setExpenseItemName(rs.getString("expenseItemName"));
        expItem.setAccountId(rs.getInt("accountId"));
        expItem.setAmount(rs.getBigDecimal("amount"));

        return expItem;
    }
}
