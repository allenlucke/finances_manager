package com.Allen.SpringFinancesServer.Budget;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class BudgetDao {

    private static final String CLASS_NAME = "BudgetDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //User may only access budgets assigned to the user
    public List<BudgetModel> getAllBudgets(final int usersId){

        final String methodName = "getAllBudgets() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"budget\".\"id\", \"budget\".\"name\", \"budget\".\"period_id\", \n" +
                "\"budget\".\"isClosed\", \"budget\".\"users_id\" FROM \"budget\"\n" +
                "JOIN \"period\" ON \"budget\".\"period_id\" = \"period\".\"id\"\n" +
                "WHERE \"budget\".\"users_id\" = ?\n" +
                "ORDER BY \"period\".\"startDate\" ASC;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetModel> result = new ArrayList<BudgetModel>();
        for(Map<String, Object> row:rows){
            BudgetModel budget = new BudgetModel();
            budget.setId((int)row.get("id"));
            budget.setName((String)row.get("name"));
            budget.setPeriodId((int)row.get("period_id"));
            budget.setClosed((boolean)row.get("isClosed"));
            budget.setUsersId((int)row.get("users_id"));

            result.add(budget);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all budgets
    public List<BudgetModel> adminGetAllBudgets(){

        final String methodName = "adminGetAllBudgets() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetModel> result = new ArrayList<BudgetModel>();
        for(Map<String, Object> row:rows){
            BudgetModel budget = new BudgetModel();
            budget.setId((int)row.get("id"));
            budget.setName((String)row.get("name"));
            budget.setPeriodId((int)row.get("period_id"));
            budget.setClosed((boolean)row.get("isClosed"));
            budget.setUsersId((int)row.get("users_id"));

            result.add(budget);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access budgets assigned to the user
    public List<BudgetModel> getBudgetById(final int budgetId, final int usersId){

        final String methodName = "getBudgetById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        BudgetModel budget = jdbcTemplate.queryForObject(
                sql, new Object[]{budgetId, usersId}, new BudgetRowMapper());

        List<BudgetModel> result = new ArrayList<BudgetModel>();
        result.add(budget);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any budget
    public List<BudgetModel> adminGetBudgetById(int id){

        final String methodName = "adminGetBudgetById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget\" WHERE \"id\" = ?;";
        BudgetModel budget = jdbcTemplate.queryForObject( sql, new Object[]{id}, new BudgetRowMapper());

        List<BudgetModel> result = new ArrayList<BudgetModel>();
        result.add(budget);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the budget will be assigned
    //may use this post call
    public List<ReturnIdModel> addBudgetReturnId(final BudgetModel budget) {

        final String methodName = "addBudgetReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"budget\"\n" +
                "\t(\"name\", \"period_id\", \"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?, ? );";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, budget.getName());
            ps.setInt(2, budget.getPeriodId());
            ps.setInt(3, budget.getUsersId());

            return ps;
        },holder);

        int id;
        if (holder.getKeys().size() > 1){
            id = (int) holder.getKeys().get("id");
        } else {
            id = (int) holder.getKey();
        }

        List<ReturnIdModel> result = new ArrayList<ReturnIdModel>();
        ReturnIdModel idObject = new ReturnIdModel();
        idObject.setId(id);

        result.add(idObject);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }
}
