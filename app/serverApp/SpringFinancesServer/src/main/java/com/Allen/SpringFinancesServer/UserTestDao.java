package com.Allen.SpringFinancesServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class UserTestDao  {

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public String getUserName(int id){
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

    public boolean addUser(final UserModel usr) {
        String sql = "INSERT INTO \"users\"\n" +
                "    (\"firstName\", \"lastName\", \"username\", \"password\", \"securityLevel\", \"email\", \"role\")\n" +
                "VALUES\n" +
                "    ( ?,?,?,?,?,?,?);";
        return jdbcTemplate.execute(sql, new PreparedStatementCallback<Boolean>() {
            @Override
            public Boolean doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
                ps.setString(1, usr.getFirstName());
                ps.setString(2, usr.getLastName());
                ps.setString(3, usr.getUserName());
                ps.setString(4, usr.getPassword());
                ps.setInt(5, usr.getSecurityLevel());
                ps.setString(6, usr.getEmail());
                ps.setString(7, usr.getRole());

                return ps.execute();
            }
        });
    }
}
