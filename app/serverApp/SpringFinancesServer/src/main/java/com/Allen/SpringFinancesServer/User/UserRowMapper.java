package com.Allen.SpringFinancesServer.User;

import com.Allen.SpringFinancesServer.Period.PeriodModel;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class UserRowMapper implements RowMapper<UserModel> {

    private static final String CLASS_NAME = "UserRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    //Maps result set returned from database to UserModel
    @Override
    public UserModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

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

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return user;
    }
}
