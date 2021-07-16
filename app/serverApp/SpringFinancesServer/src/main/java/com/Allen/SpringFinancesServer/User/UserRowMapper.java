package com.Allen.SpringFinancesServer.User;

import com.Allen.SpringFinancesServer.Period.PeriodModel;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRowMapper implements RowMapper<UserModel> {

    @Override
    public UserModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        UserModel user = new UserModel();

        user.setId(rs.getInt("id"));
        user.setFirstName(rs.getString("firstName"));
        user.setLastName(rs.getString("lastName"));
        user.setUserName(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setSecurityLevel(rs.getInt("securityLevel"));
        user.setEmail(rs.getString("email"));
        user.setRole(rs.getString("role"));
        user.setActive(rs.getBoolean("isActive"));

        return user;
    }
}
