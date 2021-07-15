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

@Service
public class PeriodDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<PeriodModel> getAllPeriods(){
        String sql = "SELECT * FROM \"period\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

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
        return result;
    }


    public List<PeriodModel> getPeriodById(int id){
        String sql = "SELECT * FROM \"period\" WHERE \"id\" = ?;";

        PeriodModel period = jdbcTemplate.queryForObject( sql, new Object[]{id}, new PeriodRowMapper());

        List<PeriodModel> result = new ArrayList<PeriodModel>();

        result.add(period);

        return result;
    }

//    public int addPeriodReturnId(final PeriodModel period) {
//        String sql = "INSERT INTO \"period\"\n" +
//                "\t(\"name\", \"startDate\", \"endDate\", \"users_id\")\n" +
//                "VALUES\n" +
//                "\t(?, ?, ?, ?) returning \"id\";";
//
//        KeyHolder holder = new GeneratedKeyHolder();
//        jdbcTemplate.update(connection -> {
//            PreparedStatement ps = connection.prepareStatement(sql,
//                    Statement.RETURN_GENERATED_KEYS);
//
//            ps.setString(1, period.getName());
//            ps.setTimestamp(2, period.getStartDate());
//            ps.setTimestamp(3, period.getEndDate());
//            ps.setInt(4, period.getUsers_id());
//
//            return ps;
//        },holder);
//        return (int) holder.getKey();
//
//    }

    public List<ReturnIdModel> addPeriodReturnId(final PeriodModel period) {
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

        return result;

    }
}
