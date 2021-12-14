package com.Allen.SpringFinancesServer.ExpenseCategory;

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

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class ExpenseCategoryDao {

    private static final String CLASS_NAME = "ExpenseCategoryDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //User may only access expense categories assigned to the user
    public List<ExpenseCategoryModel> getAllExpCats(final int usersId) {

        final String methodName = "getAllExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseCategory\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            ExpenseCategoryModel expCat = new ExpenseCategoryModel();
            expCat.setId((int)row.get("id"));
            expCat.setName((String)row.get("name"));
            expCat.setUsersId((int)row.get("users_id"));

            result.add(expCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all expense categories
    public List<ExpenseCategoryModel> adminGetAllExpCats() {

        final String methodName = "adminGetAllExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            ExpenseCategoryModel expCat = new ExpenseCategoryModel();
            expCat.setId((int)row.get("id"));
            expCat.setName((String)row.get("name"));
            expCat.setUsersId((int)row.get("users_id"));

            result.add(expCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access expense categories assigned to the user
    public List<ExpenseCategoryModel> getExpCatById(final int catId, final int usersId){

        final String methodName = "getExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseCategory\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        ExpenseCategoryModel expenseCat = jdbcTemplate.queryForObject(
                sql, new Object[]{catId, usersId}, new ExpenseCategoryRowMapper());

        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();
        result.add(expenseCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any expense category
    public List<ExpenseCategoryModel> adminGetExpCatById(final int id){

        final String methodName = "adminGetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseCategory\" WHERE \"id\" = ?;";
        ExpenseCategoryModel expenseCat = jdbcTemplate.queryForObject(
                sql, new Object[]{id}, new ExpenseCategoryRowMapper());

        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();
        result.add(expenseCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Get available expense categories not assigned to the input budget
    //User may only access expense categories assigned to the user
    public List<ExpenseCategoryModel> getExpenseCatsNotAssignedToBudget(final int budgetId, final int usersId) {

        final String methodName = "getExpenseCatsNotAssignedToBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"expenseCategory\" \n" +
                "WHERE \"expenseCategory\".\"users_id\" = ? \n" +
                "EXCEPT (SELECT \"expenseCategory\".\"id\", \"expenseCategory\".\"name\", \"expenseCategory\".\"users_id\" FROM \"expenseCategory\"\n" +
                "JOIN \"budget_expenseCategory\" ON \"expenseCategory\".\"id\" = \"budget_expenseCategory\".\"expenseCategory_id\"\n" +
                "WHERE \"budget_expenseCategory\".\"budget_id\" = ?\n" +
                "AND \"expenseCategory\".\"users_id\" = ?);";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId, budgetId, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            ExpenseCategoryModel expCat = new ExpenseCategoryModel();
            expCat.setId((int)row.get("id"));
            expCat.setName((String)row.get("name"));
            expCat.setUsersId((int)row.get("users_id"));

            result.add(expCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addExpCatReturnId(final ExpenseCategoryModel expCat) {

        final String methodName = "addExpCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"expenseCategory\"\n" +
                "\t(\"name\", \"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, expCat.getName());
            ps.setInt(2, expCat.getUsersId());

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

    //User may only delete expense categories assigned to the user
    public boolean deleteExpCatById(final int catId, final int usersId) {
        final String methodName = "deleteExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "DELETE FROM \"expenseCategory\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        int status = jdbcTemplate.update(sql, catId, usersId);
        boolean wasDeleted;

        if(status != 0) {
            LOGGER.info(CLASS_NAME + methodName + ": expenseCategory with id of " + catId + " has been deleted successfully.");
            wasDeleted = true;
        }else{
            LOGGER.info(CLASS_NAME + methodName + ": expenseCategory with id of " + catId + " cannot be found, no expenseCategory will be deleted.");
            wasDeleted = false;
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return wasDeleted;
    }
}
