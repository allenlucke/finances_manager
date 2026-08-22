package com.Allen.SpringFinancesServer.Period;

import com.Allen.SpringFinancesServer.Utils.TimestampManager;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

public class PeriodRowMapper implements RowMapper<PeriodModel> {

    private static final String CLASS_NAME = "PeriodRowMapper --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    private TimestampManager timeMgr = new TimestampManager();

    //Maps result set returned from database to PeriodModel
    @Override
    public PeriodModel mapRow(ResultSet rs, int rowNum) throws SQLException {

        final String methodName = "mapRow() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        PeriodModel period = new PeriodModel();

        period.setId(rs.getInt("id"));
        period.setName(rs.getString("name"));
        Timestamp outputStartDate = rs.getTimestamp("startDate");
        period.setStartDate(timeMgr.timestampToStringParser(outputStartDate));
        Timestamp outputEndDate = rs.getTimestamp("endDate");
        period.setEndDate(timeMgr.timestampToStringParser(outputEndDate));
        period.setUsersId(rs.getInt("users_id"));

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return period;
    }

}
