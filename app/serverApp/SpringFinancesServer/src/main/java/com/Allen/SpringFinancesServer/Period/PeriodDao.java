package com.Allen.SpringFinancesServer.Period;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class PeriodDao {

    private static final String CLASS_NAME = "PeriodDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //Admin only, may access all periods
    public List<PeriodModel> adminGetAllPeriods(){

        final String methodName = "adminGetAllPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"period\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<PeriodModel> result = new ArrayList<PeriodModel>();
        for(Map<String, Object> row:rows){
            PeriodModel period = new PeriodModel();
            period.setId((int)row.get("id"));
            period.setName((String)row.get("name"));
            period.setStartDate((Timestamp)row.get("startDate"));
            period.setEndDate((Timestamp)row.get("endDate"));
            period.setUsersId((int)row.get("users_id"));

            result.add(period);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access periods assigned to the user
    public List<PeriodModel> getAllPeriods(int usersId){

        final String methodName = "getAllPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"period\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<PeriodModel> result = new ArrayList<PeriodModel>();
        for(Map<String, Object> row:rows){
            PeriodModel period = new PeriodModel();
            period.setId((int)row.get("id"));
            period.setName((String)row.get("name"));
            period.setStartDate((Timestamp)row.get("startDate"));
            period.setEndDate((Timestamp)row.get("endDate"));
            period.setUsersId((int)row.get("users_id"));

            result.add(period);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User only may access current period assigned to user
    public List<PeriodModel> getCurrentPeriod(int usersId){

        final String methodName = "getCurrentPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"period\"\n" +
                "WHERE \"period\".\"startDate\" <= now()\n" +
                "AND \"period\".\"endDate\" >= now()\n" +
                "AND \"period\".\"users_id\" = ?;";
        PeriodModel period = jdbcTemplate.queryForObject( sql, new Object[]{usersId}, new PeriodRowMapper());

        List<PeriodModel> result = new ArrayList<PeriodModel>();
        result.add(period);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any periods
    public List<PeriodModel> adminGetPeriodById(int id){

        final String methodName = "adminGetPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"period\" WHERE \"id\" = ?;";
        PeriodModel period = jdbcTemplate.queryForObject( sql, new Object[]{id}, new PeriodRowMapper());

        List<PeriodModel> result = new ArrayList<PeriodModel>();
        result.add(period);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access periods assigned to the user
    public List<PeriodModel> getPeriodById(int periodId, int usersId){

        final String methodName = "getPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"period\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        PeriodModel period = jdbcTemplate.queryForObject( sql, new Object[]{periodId, usersId}, new PeriodRowMapper());

        List<PeriodModel> result = new ArrayList<PeriodModel>();
        result.add(period);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the period will be assigned
    //may use this post call
    public List<ReturnIdModel> addPeriodReturnId(final PeriodModel period) {

        final String methodName = "addPeriodReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"period\"\n" +
                "\t(\"name\", \"startDate\", \"endDate\", \"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?, ?, ?) returning \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, period.getName());
            ps.setTimestamp(2, period.getStartDate());
            ps.setTimestamp(3, period.getEndDate());
            ps.setInt(4, period.getUsersId());

            return ps;
        },holder);
        int id = (int) holder.getKey();

        List<ReturnIdModel> result = new ArrayList<ReturnIdModel>();
        ReturnIdModel idObject = new ReturnIdModel();
        idObject.setId(id);

        result.add(idObject);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;

    }
}
