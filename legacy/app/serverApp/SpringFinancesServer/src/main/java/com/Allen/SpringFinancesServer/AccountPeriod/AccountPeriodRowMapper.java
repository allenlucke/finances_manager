package com.Allen.SpringFinancesServer.AccountPeriod;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class AccountPeriodRowMapper implements RowMapper<AccountPeriodModel> {

    private static final String CLASS_NAME = "AccountPeriodRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public AccountPeriodModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        AccountPeriodModel acctPeriod = new AccountPeriodModel();

        acctPeriod.setId(rs.getInt("id"));
        acctPeriod.setAccountId(rs.getInt("account_id"));
        acctPeriod.setPeriodId(rs.getInt("period_id"));
        acctPeriod.setBeginningBalance(rs.getBigDecimal("beginningBalance"));
        acctPeriod.setEndingBalance(rs.getBigDecimal("endingBalance"));
        acctPeriod.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return acctPeriod;
    }
}
