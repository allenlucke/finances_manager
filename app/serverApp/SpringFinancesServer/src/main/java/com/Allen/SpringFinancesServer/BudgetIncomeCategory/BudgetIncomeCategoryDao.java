package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

import com.Allen.SpringFinancesServer.Model.BudgIncCatRespWithName;
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
public class BudgetIncomeCategoryDao {

    private static final String CLASS_NAME = "BudgetIncomeCategoryDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TimestampManager timeMgr = new TimestampManager();

    //User may only access budget income categories assigned to the user
    public List<BudgetIncomeCategoryModel> getAllBudgetIncomeCats(final int usersId) {

        final String methodName = "getAllBudgetIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_incomeCategory\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();
            budgIncomeCat.setId((int)row.get("id"));
            budgIncomeCat.setBudgetId((int)row.get("budget_id"));
            budgIncomeCat.setIncomeCategoryId((int)row.get("incomeCategory_id"));
            budgIncomeCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgIncomeCat.setUsersId((int)row.get("users_id"));

            result.add(budgIncomeCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all budget income categories
    public List<BudgetIncomeCategoryModel> adminGetAllBudgetIncomeCats() {

        final String methodName = "adminGetAllBudgetIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_incomeCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();
            budgIncomeCat.setId((int)row.get("id"));
            budgIncomeCat.setBudgetId((int)row.get("budget_id"));
            budgIncomeCat.setIncomeCategoryId((int)row.get("incomeCategory_id"));
            budgIncomeCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgIncomeCat.setUsersId((int)row.get("users_id"));

            result.add(budgIncomeCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access budget income categories assigned to the user
    public List<BudgetIncomeCategoryModel> getBudgetIncomeCatById(final int budgIncCatId, final int usersId){

        final String methodName = "getBudgetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_incomeCategory\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        BudgetIncomeCategoryModel budgetIncomeCat = jdbcTemplate.queryForObject(
                sql, new Object[]{budgIncCatId, usersId}, new BudgetIncomeCategoryRowMapper());

        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        result.add(budgetIncomeCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any budget income category
    public List<BudgetIncomeCategoryModel> adminGetBudgetIncomeCatById(final int id){

        final String methodName = "adminGetBudgetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_incomeCategory\" WHERE \"id\" = ?;";
        BudgetIncomeCategoryModel budgetIncomeCat = jdbcTemplate.queryForObject(
                sql, new Object[]{id}, new BudgetIncomeCategoryRowMapper());

        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        result.add(budgetIncomeCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addBudgetIncomeCatReturnId(final BudgetIncomeCategoryModel budgetIncomeCat) {

        final String methodName = "addBudgetIncomeCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"budget_incomeCategory\"\n" +
                "\t(\"budget_id\", \"incomeCategory_id\", \"amountBudgeted\", \"users_id\" )\n" +
                "VALUES\n" +
                "\t(?, ?, ?, ? );";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, budgetIncomeCat.getBudgetId());
            ps.setInt(2, budgetIncomeCat.getIncomeCategoryId());
            ps.setBigDecimal(3, budgetIncomeCat.getAmountBudgeted());
            ps.setInt(4,budgetIncomeCat.getUsersId());

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
    public List<BudgetIncomeCategoryModel> getBudgetIncCatByIncCat(final int budgetId, final int usersId){

        final String methodName = "getBudgetIncCatByIncCat() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"budget_incomeCategory\" WHERE \n" +
                "\"budget_incomeCategory\".\"budget_id\" = ?\n" +
                "AND \"budget_incomeCategory\".\"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {budgetId, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();
            budgIncomeCat.setId((int)row.get("id"));
            budgIncomeCat.setBudgetId((int)row.get("budget_id"));
            budgIncomeCat.setIncomeCategoryId((int)row.get("incomeCategory_id"));
            budgIncomeCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgIncomeCat.setUsersId((int)row.get("users_id"));

            result.add(budgIncomeCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Returns all budget income categories from the period the date falls within
    //User may only access budget income categories assigned to the user
    public List<BudgetIncomeCategoryModel> getBudgetIncCatsByDate(final String date, final int usersId) {

        final String methodName = "getBudgetIncCatsByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        Timestamp dateToSearch = timeMgr.stringToTimestampParser(date);

        String sql = "SELECT \"budget_incomeCategory\".\"id\", \"budget_incomeCategory\".\"budget_id\",\n" +
                "\"budget_incomeCategory\".\"incomeCategory_id\", \"budget_incomeCategory\".\"amountBudgeted\",\n" +
                "\"budget_incomeCategory\".\"users_id\" FROM \"budget_incomeCategory\"\n" +
                "JOIN \"budget\" ON \"budget_incomeCategory\".\"budget_id\" = \"budget\".\"id\"\n" +
                "JOIN \"period\" ON \"budget\".\"period_id\" = \"period\".\"id\"\n" +
                "WHERE ? >= \"startDate\"\n" +
                "AND ? <= \"endDate\"\n" +
                "AND \"budget_incomeCategory\".\"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {dateToSearch, dateToSearch, usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();
            budgIncomeCat.setId((int)row.get("id"));
            budgIncomeCat.setBudgetId((int)row.get("budget_id"));
            budgIncomeCat.setIncomeCategoryId((int)row.get("incomeCategory_id"));
            budgIncomeCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));
            budgIncomeCat.setUsersId((int)row.get("users_id"));

            result.add(budgIncomeCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Returns all budget income categories from the period the date falls within
    //User may only access budget income categories assigned to the user
    public List<BudgIncCatRespWithName> getBudgetIncCatsWithNameByDate(final String date, final int usersId) {

        final String methodName = "getBudgetIncCatsWithNameByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        Timestamp dateToSearch = timeMgr.stringToTimestampParser(date);

        String sql = "SELECT \"budget_incomeCategory\".\"id\", \"budget_incomeCategory\".\"budget_id\",\n" +
                "\"budget_incomeCategory\".\"incomeCategory_id\", \"budget_incomeCategory\".\"amountBudgeted\",\n" +
                "\"budget_incomeCategory\".\"users_id\", \"incomeCategory\".\"name\" FROM \"budget_incomeCategory\"\n" +
                "JOIN \"budget\" ON \"budget_incomeCategory\".\"budget_id\" = \"budget\".\"id\"\n" +
                "JOIN \"period\" ON \"budget\".\"period_id\" = \"period\".\"id\"\n" +
                "JOIN \"incomeCategory\" ON \"budget_incomeCategory\".\"incomeCategory_id\" = \"incomeCategory\".\"id\"\n" +
                "WHERE ? >= \"startDate\"\n" +
                "AND ? <= \"endDate\"\n" +
                "AND \"budget_incomeCategory\".\"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[]{dateToSearch, dateToSearch, usersId});

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<BudgIncCatRespWithName> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            BudgIncCatRespWithName budgIncomeCat = new BudgIncCatRespWithName();
            budgIncomeCat.setId((int) row.get("id"));
            budgIncomeCat.setBudgetId((int) row.get("budget_id"));
            budgIncomeCat.setIncomeCategoryId((int) row.get("incomeCategory_id"));
            budgIncomeCat.setAmountBudgeted((BigDecimal) row.get("amountBudgeted"));
            budgIncomeCat.setUsersId((int) row.get("users_id"));
            budgIncomeCat.setName((String)row.get("name"));

            result.add(budgIncomeCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }
}
