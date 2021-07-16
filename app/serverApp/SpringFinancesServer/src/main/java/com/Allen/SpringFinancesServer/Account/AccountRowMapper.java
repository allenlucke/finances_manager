package com.Allen.SpringFinancesServer.Account;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AccountRowMapper implements RowMapper<AccountModel> {

    @Override
    public AccountModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        AccountModel account = new AccountModel();

        account.setId(rs.getInt("id"));
        account.setName(rs.getString("name"));
        account.setUsersId(rs.getInt("users_id"));
        account.setCredit(rs.getBoolean("isCredit"));
        account.setActive(rs.getBoolean("isActive"));

        return account;
    }
}