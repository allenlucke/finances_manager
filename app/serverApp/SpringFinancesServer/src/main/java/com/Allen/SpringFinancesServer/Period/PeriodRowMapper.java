package com.Allen.SpringFinancesServer.Period;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class PeriodRowMapper implements RowMapper<PeriodModel> {

    private static final String CLASS_NAME = "PeriodRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    //Maps result set returned from database to PeriodModel
    @Override
    public PeriodModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        PeriodModel period = new PeriodModel();

        period.setId(rs.getInt("id"));
        period.setName(rs.getString("name"));
        period.setStartDate(rs.getTimestamp("startDate"));
        period.setEndDate(rs.getTimestamp("endDate"));
        period.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return period;
    }
}
