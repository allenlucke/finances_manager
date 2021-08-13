package com.Allen.SpringFinancesServer.IncomeCategory;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class IncomeCategoryRowMapper implements RowMapper<IncomeCategoryModel>{

    @Override
    public IncomeCategoryModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        IncomeCategoryModel incomeCat = new IncomeCategoryModel();

        incomeCat.setId(rs.getInt("id"));
        incomeCat.setName(rs.getString("name"));
        incomeCat.setUsersId(rs.getInt("users_id"));

        return incomeCat;
    }
}
