package com.Allen.SpringFinancesServer.AccountPeriod;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AccountPeriodRowMapper implements RowMapper<AccountPeriodModel> {

    @Override
    public AccountPeriodModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        AccountPeriodModel acctPeriod = new AccountPeriodModel();

        acctPeriod.setId(rs.getInt("id"));
        acctPeriod.setAccountId(rs.getInt("account_id"));
        acctPeriod.setPeriodId(rs.getInt("period_id"));
        acctPeriod.setBeginningBalance(rs.getBigDecimal("beginningBalance"));
        acctPeriod.setEndingBalance(rs.getBigDecimal("endingBalance"));
        acctPeriod.setUsersId(rs.getInt("users_id"));

        return acctPeriod;
    }
}
