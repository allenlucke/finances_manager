package com.Allen.SpringFinancesServer.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class UserDao {

    private static final String CLASS_NAME = "UserDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public String getUserFirstName(final int id){

        final String methodName = "getUserFirstName() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sqlQuery = "SELECT \"firstName\" FROM \"users\" WHERE \"id\" = ?;";

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
		return jdbcTemplate.queryForObject( sqlQuery, new Object[]{id}, String.class);
    }

    //Admin ONLY!
    public List<UserModel> getAllUsers(){

        final String methodName = "getAllUsers() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"users\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<UserModel> result = new ArrayList<UserModel>();

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        for(Map<String, Object> row:rows){
            UserModel usr = new UserModel();
            usr.setId((int)row.get("id"));
            usr.setFirstName((String)row.get("firstName"));
            usr.setLastName((String)row.get("lastName"));
            usr.setUserName((String)row.get("userName"));
            usr.setPassword((String)row.get("password"));
            usr.setSecurityLevel((int)row.get("securityLevel"));
            usr.setEmail((String)row.get("email"));
            usr.setRole((String)row.get("role"));
            usr.setActive((boolean)row.get("isActive"));
            result.add(usr);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    public List<UserModel> getUserById(final int id){

        final String methodName = "getUserById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"users\" WHERE \"id\" = ?;";

        UserModel usr = jdbcTemplate.queryForObject( sql, new Object[]{id}, new UserRowMapper());
        List<UserModel> result = new ArrayList<UserModel>();

        LOGGER.info(CLASS_NAME + methodName + "Adding result set to list");
        result.add(usr);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Used for application Client for local storage user data
    public List<ClientLocalStorageUser> getUserByToken(final int id){

        final String methodName = "getUserByToken() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"id\", \"firstName\", \"lastName\", \"username\",\n" +
                "\"email\", \"role\" FROM \"users\" WHERE \"id\" = ?;";

        ClientLocalStorageUser usr = jdbcTemplate.queryForObject( sql, new Object[]{id}, new ClientLocalStorageUserRowMapper());
        List<ClientLocalStorageUser> result = new ArrayList<ClientLocalStorageUser>();

        LOGGER.info(CLASS_NAME + methodName + "Adding result set to list");
        result.add(usr);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    public int addUserReturnId(final UserModel usr) {

        final String methodName = "addUserReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"users\"(\"firstName\", \"lastName\", \"username\", \"password\", \"securityLevel\", \"email\", \"role\")\n" +
                "VALUES( ?,?,?,?,?,?,?) returning \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, usr.getFirstName());
            ps.setString(2, usr.getLastName());
            ps.setString(3, usr.getUserName());
            ps.setString(4, usr.getPassword());
            ps.setInt(5, usr.getSecurityLevel());
            ps.setString(6, usr.getEmail());
            ps.setString(7, usr.getRole());

            LOGGER.info(CLASS_NAME +  methodName + "Executing prepared statement");

            return ps;
        },holder);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return (int) holder.getKey();
    }

    //Used by JWTAuthenticationController - No Auth required
    public UserModel getUserByUsername(final String username){

        final String methodName = "getUserByUsername() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"users\" WHERE \"username\" = ?;";

        UserModel usr = jdbcTemplate.queryForObject( sql, new Object[]{username}, new UserRowMapper());

        List<UserModel> result = new ArrayList<UserModel>();
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return usr;
    }

    //Used by JWTAuthenticationController? - No Auth required
    //Method uses longhand style instead of row mapper
    public UserModel addUserReturnUser(final UserModel usr) {

        final String methodName = "addUserReturnUser() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"users\"(\"firstName\", \"lastName\", \"username\", \"password\", \"securityLevel\", \"email\", \"role\")\n" +
                "VALUES( ?,?,?,?,?,?,?);";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, usr.getFirstName());
            ps.setString(2, usr.getLastName());
            ps.setString(3, usr.getUserName());
            ps.setString(4, usr.getPassword());
            ps.setInt(5, usr.getSecurityLevel());
            ps.setString(6, usr.getEmail());
            ps.setString(7, usr.getRole());

            LOGGER.info(CLASS_NAME +  methodName + "Executing prepared statement");

            return ps;
        },holder);

        //Assign key values to variables
        int id = (int) holder.getKeys().get("id");
        String firstName = (String) holder.getKeys().get("firstName");
        String lastName = (String) holder.getKeys().get("lastName");
        String userName = (String) holder.getKeys().get("userName");
        String password = (String) holder.getKeys().get("password");
        int securityLevel = (int) holder.getKeys().get("securityLevel");
        String email = (String) holder.getKeys().get("email");
        String role = (String) holder.getKeys().get("role");
        boolean isActive = (boolean) holder.getKeys().get("isActive");

        //Set key variable to user object
        UserModel user = new UserModel();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUserName(userName);
        user.setPassword(password);
        user.setSecurityLevel(securityLevel);
        user.setEmail(email);
        user.setRole(role);
        user.setActive(isActive);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return user;
    }
}
