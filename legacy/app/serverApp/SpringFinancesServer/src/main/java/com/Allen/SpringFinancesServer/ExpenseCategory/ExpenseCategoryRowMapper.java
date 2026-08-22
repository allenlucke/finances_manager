package com.Allen.SpringFinancesServer.ExpenseCategory;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class ExpenseCategoryRowMapper implements RowMapper<ExpenseCategoryModel> {

    private static final String CLASS_NAME = "ExpenseCategoryRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Override
    public ExpenseCategoryModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ExpenseCategoryModel expCat = new ExpenseCategoryModel();

        expCat.setId(rs.getInt("id"));
        expCat.setName(rs.getString("name"));
        expCat.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return expCat;
    }
}
