package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

import com.Allen.SpringFinancesServer.Model.BudgExpCatRespWithName;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import com.Allen.SpringFinancesServer.Utils.TimestampManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class BudgetExpenseCategoryDao {

    private static final String CLASS_NAME = "BudgetExpenseCategoryDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TimestampManager timeMgr = new TimestampManager();

    //User may only access budget expense categories assigned to the user
    public List<BudgetExpenseCategoryModel> getAllBudgetExpCats(final int usersId) {

        final String methodName = "getAllBudgetExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_expenseCategory\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetExpenseCategoryModel> result = new ArrayList<BudgetExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetExpenseCategoryModel budgExpCat = new BudgetExpenseCategoryModel();
            budgExpCat.setId((int)row.get("id"));
            budgExpCat.setBudgetId((int)row.get("budget_id"));
            budgExpCat.setExpenseCategoryId((int)row.get("expenseCategory_id"));
            budgExpCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgExpCat.setUsersId((int)row.get("users_id"));

            result.add(budgExpCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Returns all budget expense categories from the period the date falls within
    //User may only access budget expense categories assigned to the user
    public List<BudgetExpenseCategoryModel> getBudgetExpCatsByDate(final String date, final int usersId) {

        final String methodName = "getBudgetExpCatsByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        Timestamp dateToSearch = timeMgr.stringToTimestampParser(date);

        String sql = "SELECT \"budget_expenseCategory\".\"id\", \"budget_expenseCategory\".\"budget_id\",\n" +
                "\"budget_expenseCategory\".\"expenseCategory_id\", \"budget_expenseCategory\".\"amountBudgeted\", \n" +
                "\"budget_expenseCategory\".\"users_id\" FROM \"budget_expenseCategory\"\n" +
                "JOIN \"budget\" ON \"budget_expenseCategory\".\"budget_id\" = \"budget\".\"id\"\n" +
                "JOIN \"period\" ON \"budget\".\"period_id\" = \"period\".\"id\"\n" +
                "WHERE ? >= \"startDate\"\n" +
                "AND ? <= \"endDate\"\n" +
                "AND \"budget_expenseCategory\".\"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {dateToSearch, dateToSearch, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetExpenseCategoryModel> result = new ArrayList<BudgetExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetExpenseCategoryModel budgExpCat = new BudgetExpenseCategoryModel();
            budgExpCat.setId((int)row.get("id"));
            budgExpCat.setBudgetId((int)row.get("budget_id"));
            budgExpCat.setExpenseCategoryId((int)row.get("expenseCategory_id"));
            budgExpCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgExpCat.setUsersId((int)row.get("users_id"));

            result.add(budgExpCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Returns all budget expense categories from the period the date falls within
    //User may only access budget expense categories assigned to the user
    public List<BudgExpCatRespWithName> getBudgetExpCatsWithNameByDate(final String date, final int usersId) {

        final String methodName = "getBudgetExpCatsWithNameByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        Timestamp dateToSearch = timeMgr.stringToTimestampParser(date);

        String sql = "SELECT \"budget_expenseCategory\".\"id\", \"budget_expenseCategory\".\"budget_id\",\n" +
                "\"budget_expenseCategory\".\"expenseCategory_id\", \"budget_expenseCategory\".\"amountBudgeted\", \n" +
                "\"budget_expenseCategory\".\"users_id\", \"expenseCategory\".\"name\" FROM \"budget_expenseCategory\"\n" +
                "JOIN \"budget\" ON \"budget_expenseCategory\".\"budget_id\" = \"budget\".\"id\"\n" +
                "JOIN \"period\" ON \"budget\".\"period_id\" = \"period\".\"id\"\n" +
                "JOIN \"expenseCategory\" ON \"budget_expenseCategory\".\"expenseCategory_id\" = \"expenseCategory\".\"id\"\n" +
                "WHERE ? >= \"startDate\"\n" +
                "AND ? <= \"endDate\"\n" +
                "AND \"budget_expenseCategory\".\"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {dateToSearch, dateToSearch, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgExpCatRespWithName> result = new ArrayList<BudgExpCatRespWithName>();
        for(Map<String, Object> row:rows){
            BudgExpCatRespWithName budgExpCat = new BudgExpCatRespWithName();
            budgExpCat.setId((int)row.get("id"));
            budgExpCat.setBudgetId((int)row.get("budget_id"));
            budgExpCat.setExpenseCategoryId((int)row.get("expenseCategory_id"));
            budgExpCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgExpCat.setUsersId((int)row.get("users_id"));
            budgExpCat.setName((String)row.get("name"));

            result.add(budgExpCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all budget expense categories
    public List<BudgetExpenseCategoryModel> adminGetAllBudgetExpCats() {

        final String methodName = "adminGetAllBudgetExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_expenseCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetExpenseCategoryModel> result = new ArrayList<BudgetExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetExpenseCategoryModel budgExpCat = new BudgetExpenseCategoryModel();
            budgExpCat.setId((int)row.get("id"));
            budgExpCat.setBudgetId((int)row.get("budget_id"));
            budgExpCat.setExpenseCategoryId((int)row.get("expenseCategory_id"));
            budgExpCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgExpCat.setUsersId((int)row.get("users_id"));

            result.add(budgExpCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access budget expense categories assigned to the user
    public List<BudgetExpenseCategoryModel> getBudgetExpCatById(final int budgExpCatId, final int usersId){

        final String methodName = "getBudgetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_expenseCategory\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        BudgetExpenseCategoryModel budgetExpCat = jdbcTemplate.queryForObject(
                sql, new Object[]{budgExpCatId, usersId}, new BudgetExpenseCategoryRowMapper());

        List<BudgetExpenseCategoryModel> result = new ArrayList<BudgetExpenseCategoryModel>();
        result.add(budgetExpCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any budget expense category
    public List<BudgetExpenseCategoryModel> adminGetBudgetExpCatById(int id){

        final String methodName = "adminGetBudgetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_expenseCategory\" WHERE \"id\" = ?;";
        BudgetExpenseCategoryModel budgetExpCat = jdbcTemplate.queryForObject(
                sql, new Object[]{id}, new BudgetExpenseCategoryRowMapper());

        List<BudgetExpenseCategoryModel> result = new ArrayList<BudgetExpenseCategoryModel>();
        result.add(budgetExpCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addBudgetExpCatReturnId(final BudgetExpenseCategoryModel budgetExpCat) {

        final String methodName = "addBudgetExpCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"budget_expenseCategory\"\n" +
                "(\"budget_id\", \"expenseCategory_id\", \"amountBudgeted\", \"users_id\" )\n" +
                "VALUES(?, ?, ?, ? ) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, budgetExpCat.getBudgetId());
            ps.setInt(2, budgetExpCat.getExpenseCategoryId());
            ps.setBigDecimal(3, budgetExpCat.getAmountBudgeted());
            ps.setInt(4, budgetExpCat.getUsersId());

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

    //User may only access budget expense categories assigned to the user
    public List<BudgetExpenseCategoryModel> getBudgetExpCatByExpCat(final int budgetId, final int usersId){

        final String methodName = "getBudgetExpCatByExpCatNUsersId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_expenseCategory\" WHERE \n" +
                "\"budget_expenseCategory\".\"budget_id\" = ?\n" +
                "AND \"budget_expenseCategory\".\"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {budgetId, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetExpenseCategoryModel> result = new ArrayList<BudgetExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetExpenseCategoryModel budgExpCat = new BudgetExpenseCategoryModel();
            budgExpCat.setId((int)row.get("id"));
            budgExpCat.setBudgetId((int)row.get("budget_id"));
            budgExpCat.setExpenseCategoryId((int)row.get("expenseCategory_id"));
            budgExpCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgExpCat.setUsersId((int)row.get("users_id"));

            result.add(budgExpCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

}
