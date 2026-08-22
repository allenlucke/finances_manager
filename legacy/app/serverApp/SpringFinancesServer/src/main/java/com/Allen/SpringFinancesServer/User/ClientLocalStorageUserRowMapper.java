package com.Allen.SpringFinancesServer.User;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class ClientLocalStorageUserRowMapper implements RowMapper<ClientLocalStorageUser> {

    private static final String CLASS_NAME = "ClientLocalStorageUserRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    //Maps result set returned from database to UserModel
    @Override
    public ClientLocalStorageUser mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ClientLocalStorageUser user = new ClientLocalStorageUser();

        user.setId(rs.getInt("id"));
        user.setFirstName(rs.getString("firstName"));
        user.setLastName(rs.getString("lastName"));
        user.setUserName(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setRole(rs.getString("role"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return user;
    }
}
