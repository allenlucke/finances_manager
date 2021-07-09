package com.Allen.SpringFinancesServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.JdbcDaoSupport;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserTestDao  {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public String getUserName(int id){
        String sqlQuery = "SELECT \"firstName\" FROM \"users\" WHERE \"id\" = ?;";
		return jdbcTemplate.queryForObject( sqlQuery, new Object[]{id}, String.class);
    }

//    public List getUsername(int id) {
//
////        String sqlQueryString = "SELECT \"firstName\" FROM \"users\" WHERE \"id\" = ?;";
//
//        String sqlQueryString = "SELECT \"firstName\" FROM \"users\";";
//
//        return getJdbcTemplate().query(sqlQueryString, new ResultSetExtractor<List<UserModel>>() {
//            @Override
//            public List extractData(ResultSet rs) throws SQLException, DataAccessException {
//
//                List<UserModel> usrList = new ArrayList<UserModel>();
//                while(rs.next()){
//                UserModel usr = new UserModel();
//                usr.setFirstName(rs.getString(1));
//                usrList.add(usr);
//                }
//                return usrList;
//            }
//        });



}
