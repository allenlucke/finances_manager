package com.Allen.SpringFinancesServer.Budget;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BudgetDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<BudgetModel> getAllBudgets(){
        String sql = "SELECT * FROM \"budget\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<BudgetModel> result = new ArrayList<BudgetModel>();
        for(Map<String, Object> row:rows){
            BudgetModel budget = new BudgetModel();
            budget.setId((int)row.get("id"));
            budget.setName((String)row.get("name"));
            budget.setPeriodId((int)row.get("period_id"));
            budget.setClosed((boolean)row.get("isClosed"));

            result.add(budget);
        }
        return result;
    }

    public List<BudgetModel> getBudgetById(int id){
        String sql = "SELECT * FROM \"budget\" WHERE \"id\" = ?;";

        BudgetModel budget = jdbcTemplate.queryForObject( sql, new Object[]{id}, new BudgetRowMapper());

        List<BudgetModel> result = new ArrayList<BudgetModel>();

        result.add(budget);

        return result;
    }

//    public int addBudgetReturnId(final BudgetModel budget) {
//        String sql = "INSERT INTO \"budget\"\n" +
//                "\t(\"name\", \"period_id\")\n" +
//                "VALUES\n" +
//                "\t(?, ?) RETURNING \"id\";";
//
//        KeyHolder holder = new GeneratedKeyHolder();
//        jdbcTemplate.update(connection -> {
//            PreparedStatement ps = connection.prepareStatement(sql,
//                    Statement.RETURN_GENERATED_KEYS);
//
//            ps.setString(1, budget.getName());
//            ps.setInt(2, budget.getPeriod_id());
//
//            return ps;
//        },holder);
//        return (int) holder.getKey();
//
//    }

    public List<ReturnIdModel> addBudgetReturnId(final BudgetModel budget) {
        String sql = "INSERT INTO \"budget\"\n" +
                "\t(\"name\", \"period_id\")\n" +
                "VALUES\n" +
                "\t(?, ?) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, budget.getName());
            ps.setInt(2, budget.getPeriodId());

            return ps;
        },holder);

        int id = (int) holder.getKey();

        List<ReturnIdModel> result = new ArrayList<ReturnIdModel>();
        ReturnIdModel idObject = new ReturnIdModel();
        idObject.setId(id);

        result.add(idObject);

    //    return (int) holder.getKey();
        return result;
    }
}
