package com.Allen.SpringFinancesServer.ExpenseCategory;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ExpenseCategoryRowMapper implements RowMapper<ExpenseCategoryModel> {


    @Override
    public ExpenseCategoryModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        ExpenseCategoryModel expCat = new ExpenseCategoryModel();

        expCat.setId(rs.getInt("id"));
        expCat.setName(rs.getString("name"));
        expCat.setUsersId(rs.getInt("users_id"));

        return expCat;
    }
}
