package com.Allen.SpringFinancesServer.Account;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class AccountRowMapper implements RowMapper<AccountModel> {

    private static final String CLASS_NAME = "AccountRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public AccountModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        AccountModel account = new AccountModel();

        account.setId(rs.getInt("id"));
        account.setName(rs.getString("name"));
        account.setUsersId(rs.getInt("users_id"));
        account.setCredit(rs.getBoolean("isCredit"));
        account.setActive(rs.getBoolean("isActive"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return account;
    }
}