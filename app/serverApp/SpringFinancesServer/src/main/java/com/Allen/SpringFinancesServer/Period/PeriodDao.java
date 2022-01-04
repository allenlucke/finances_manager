package com.Allen.SpringFinancesServer.Period;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import com.Allen.SpringFinancesServer.Utils.TimestampManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
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

    private TimestampManager timeMgr = new TimestampManager();

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
            Timestamp startDate = (Timestamp) row.get("startDate");
            period.setStartDate((String) timeMgr.timestampToStringParser(startDate));
            Timestamp endDate = (Timestamp) row.get("endDate");
            period.setEndDate((String) timeMgr.timestampToStringParser(endDate));
            period.setUsersId((int)row.get("users_id"));

            result.add(period);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access periods assigned to the user
    public List<PeriodModel> getAllPeriods(final int usersId){

        final String methodName = "getAllPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"period\" WHERE \"users_id\" = ?\n" +
                "ORDER BY \"period\".\"startDate\" ASC;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<PeriodModel> result = new ArrayList<PeriodModel>();
        for(Map<String, Object> row:rows){
            PeriodModel period = new PeriodModel();
            period.setId((int)row.get("id"));
            period.setName((String)row.get("name"));
            Timestamp startDate = (Timestamp) row.get("startDate");
            period.setStartDate((String) timeMgr.timestampToStringParser(startDate));
            Timestamp endDate = (Timestamp) row.get("endDate");
            period.setEndDate((String) timeMgr.timestampToStringParser(endDate));
            period.setUsersId((int)row.get("users_id"));

            result.add(period);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User only may access current period assigned to user
    public List<PeriodModel> getCurrentPeriod(final int usersId){

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

    //User may only access accounts assigned to the user
    public List<PeriodModel> getPeriodByDate(final String date, final int usersId)throws EmptyResultDataAccessException{

        final String methodName = "getPeriodByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        Timestamp dateToSearch = timeMgr.stringToTimestampParser(date);
        String sql = "SELECT * FROM \"period\" WHERE ? >= \"startDate\"\n" +
                "AND ? <= \"endDate\"\n" +
                "AND \"users_id\" = ?;";
        PeriodModel period = jdbcTemplate.queryForObject( sql, new Object[]{dateToSearch, dateToSearch, usersId}, new PeriodRowMapper());

        List<PeriodModel> result = new ArrayList<PeriodModel>();
        result.add(period);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access accounts assigned to the user
    public List<PeriodModel> getPeriodsByDateRange(final String beginningDate, final String endingDate, final int usersId){

        final String methodName = "getPeriodsByDateRange() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        Timestamp beginningDateToSearch = timeMgr.stringToTimestampParser(beginningDate);
        Timestamp endingDateToSearch = timeMgr.stringToTimestampParser(endingDate);
        String sql = "SELECT * FROM \"period\" WHERE \"startDate\" >= ? \n" +
                "AND ? >= \"startDate\"\n" +
                "AND \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {beginningDateToSearch, endingDateToSearch, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<PeriodModel> result = new ArrayList<PeriodModel>();
        for(Map<String, Object> row:rows){
            PeriodModel period = new PeriodModel();
            period.setId((int)row.get("id"));
            period.setName((String)row.get("name"));
            Timestamp startDate = (Timestamp) row.get("startDate");
            period.setStartDate((String) timeMgr.timestampToStringParser(startDate));
            Timestamp endDate = (Timestamp) row.get("endDate");
            period.setEndDate((String) timeMgr.timestampToStringParser(endDate));
            period.setUsersId((int)row.get("users_id"));

            result.add(period);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any periods
    public List<PeriodModel> adminGetPeriodById(final int id){

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
    public List<PeriodModel> getPeriodById(final int periodId, final int usersId){

        final String methodName = "getPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"period\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        PeriodModel period = jdbcTemplate.queryForObject( sql, new Object[]{periodId, usersId}, new PeriodRowMapper());

        List<PeriodModel> result = new ArrayList<PeriodModel>();
        result.add(period);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access periods assigned to the user
    public List<PeriodModel> getOverlappingPeriods(PeriodModel period, final int usersId) throws EmptyResultDataAccessException {

        final String methodName = "getOverlappingPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        Timestamp startDate = timeMgr.stringToTimestampParser(period.getStartDate());
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName + "STARTDATE = " + startDate);
        Timestamp endDate = timeMgr.stringToTimestampParser(period.getEndDate());
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName + "ENDDATE = " + endDate);

        String sql = "SELECT * FROM period WHERE \"users_id\" = ?\n" +
                "AND \"period\".\"startDate\" >= ?\n" +
                "AND \"period\".\"endDate\" <= ?\n" +
                "OR \"period\".\"startDate\" <= ?\n" +
                "AND \"period\".\"endDate\" >= ? ;";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId, startDate, endDate, endDate, startDate} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<PeriodModel> result = new ArrayList<PeriodModel>();
        for(Map<String, Object> row:rows){
            PeriodModel overlappingPeriod = new PeriodModel();
            overlappingPeriod.setId((int)row.get("id"));
            overlappingPeriod.setName((String)row.get("name"));
            Timestamp outputStartDate = (Timestamp) row.get("startDate");
            overlappingPeriod.setStartDate(timeMgr.timestampToStringParser(outputStartDate));
            Timestamp outputEndDate = (Timestamp) row.get("endDate");
            overlappingPeriod.setEndDate(timeMgr.timestampToStringParser(outputEndDate));
            overlappingPeriod.setUsersId((int)row.get("users_id"));

            result.add(overlappingPeriod);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access periods assigned to the user
    //Get a list of periods that no budget has been assigned to
    public List <PeriodModel> getPeriodsWithoutBudget(final int usersId) {

        final String methodName = "getPeriodsWithoutBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"period\" WHERE \"period\".\"users_id\" = ?\n" +
                "EXCEPT (SELECT \"period\".\"id\", \"period\".\"name\", \"period\".\"startDate\", \n" +
                "\"period\".\"endDate\", \"period\".\"users_id\" FROM \"period\"\n" +
                "JOIN \"budget\" ON \"period\".\"id\" = \"budget\".\"period_id\"\n" +
                "WHERE \"period\".\"users_id\" = ?);";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<PeriodModel> result = new ArrayList<PeriodModel>();
        for(Map<String, Object> row:rows){
            PeriodModel period = new PeriodModel();
            period.setId((int)row.get("id"));
            period.setName((String)row.get("name"));
            Timestamp startDate = (Timestamp) row.get("startDate");
            period.setStartDate((String) timeMgr.timestampToStringParser(startDate));
            Timestamp endDate = (Timestamp) row.get("endDate");
            period.setEndDate((String) timeMgr.timestampToStringParser(endDate));
            period.setUsersId((int)row.get("users_id"));

            result.add(period);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the period will be assigned
    //may use this post call
    public List<ReturnIdModel> addPeriodReturnId(final PeriodModel period) {

        final String methodName = "addPeriodReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        System.out.println("name: " + period.getName());

        String sql = "INSERT INTO \"period\"\n" +
                "\t(\"name\", \"startDate\", \"endDate\", \"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?, ?, ?) returning \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, period.getName());
            Timestamp startDateAsTimestamp = timeMgr.stringToTimestampParser(period.getStartDate());
            ps.setTimestamp(2, startDateAsTimestamp);
            Timestamp endDateAsTimestamp = timeMgr.stringToTimestampParser(period.getEndDate());
            ps.setTimestamp(3, endDateAsTimestamp);
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
