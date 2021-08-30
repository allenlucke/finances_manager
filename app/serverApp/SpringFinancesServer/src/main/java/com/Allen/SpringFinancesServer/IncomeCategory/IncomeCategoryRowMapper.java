package com.Allen.SpringFinancesServer.IncomeCategory;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class IncomeCategoryRowMapper implements RowMapper<IncomeCategoryModel>{

    private static final String CLASS_NAME = "IncomeItemRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    //Maps result set returned from database to IncomeCategoryModel
    @Override
    public IncomeCategoryModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        IncomeCategoryModel incomeCat = new IncomeCategoryModel();

        incomeCat.setId(rs.getInt("id"));
        incomeCat.setName(rs.getString("name"));
        incomeCat.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return incomeCat;
    }
}
