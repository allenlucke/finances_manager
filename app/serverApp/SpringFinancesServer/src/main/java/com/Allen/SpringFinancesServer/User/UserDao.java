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

@Service
public class UserDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public String getUserFirstName(int id){
        String sqlQuery = "SELECT \"firstName\" FROM \"users\" WHERE \"id\" = ?;";
		return jdbcTemplate.queryForObject( sqlQuery, new Object[]{id}, String.class);
    }

    public List<UserModel> getAllUsers(){
//        String sql = "SELECT \"firstName\", \"lastName\" FROM \"users\";";
        String sql = "SELECT * FROM \"users\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<UserModel> result = new ArrayList<UserModel>();
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
        return result;
    }

    public List<UserModel> getUserById(int id){
        String sql = "SELECT * FROM \"users\" WHERE \"id\" = ?;";

        UserModel usr = jdbcTemplate.queryForObject( sql, new Object[]{id}, new UserRowMapper());

        List<UserModel> result = new ArrayList<UserModel>();

        result.add(usr);

        return result;
    }

//    public boolean addUser(final UserModel usr) {
//        String sql = "INSERT INTO \"users\"\n" +
//                "    (\"firstName\", \"lastName\", \"username\", \"password\", \"securityLevel\", \"email\", \"role\")\n" +
//                "VALUES\n" +
//                "    ( ?,?,?,?,?,?,?);";
//        return jdbcTemplate.execute(sql, new PreparedStatementCallback<Boolean>() {
//            @Override
//            public Boolean doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
//                ps.setString(1, usr.getFirstName());
//                ps.setString(2, usr.getLastName());
//                ps.setString(3, usr.getUserName());
//                ps.setString(4, usr.getPassword());
//                ps.setInt(5, usr.getSecurityLevel());
//                ps.setString(6, usr.getEmail());
//                ps.setString(7, usr.getRole());
//
//                return ps.execute();
//            }
//        });

        public int addUserReturnId(final UserModel usr) {
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

                return ps;
            },holder);
            return (int) holder.getKey();

    }

    public UserModel getUserByUsername(final String username){
        String sql = "SELECT * FROM \"users\" WHERE \"username\" = ?;";

        UserModel usr = jdbcTemplate.queryForObject( sql, new Object[]{username}, new UserRowMapper());

        List<UserModel> result = new ArrayList<UserModel>();

//        result.add(usr);

        return usr;
    }

    public UserModel addUserReturnUser(final UserModel usr) {
//            String sql = "INSERT INTO \"users\"(\"firstName\", \"lastName\", \"username\", \"password\", \"securityLevel\", \"email\", \"role\")\n" +
//                    "VALUES( ?,?,?,?,?,?,?) returning \"id\";";

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

        return user;
    }
}
