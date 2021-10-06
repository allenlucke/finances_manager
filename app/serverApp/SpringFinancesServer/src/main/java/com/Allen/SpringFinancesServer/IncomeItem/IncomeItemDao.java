package com.Allen.SpringFinancesServer.IncomeItem;

import com.Allen.SpringFinancesServer.Period.PeriodModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
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
public class IncomeItemDao {

    private static final String CLASS_NAME = "IncomeItemDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //Admin only, may access all income items
    public List<IncomeItemModel> adminGetAllIncomeItems() {

        final String methodName = "adminGetAllIncomeItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"incomeItem\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<IncomeItemModel> result = new ArrayList<IncomeItemModel>();
        for(Map<String, Object> row:rows){
            IncomeItemModel incomeItem = new IncomeItemModel();
            incomeItem.setId((int)row.get("id"));
            incomeItem.setBudgetIncomeCategoryId((int)row.get("budget_incomeCategory_id"));
            incomeItem.setName((String)row.get("name"));
            incomeItem.setReceivedDate((Timestamp)row.get("receivedDate"));
            incomeItem.setAmountExpected((BigDecimal)row.get("amountExpected"));
            incomeItem.setAmountReceived((BigDecimal)row.get("amountReceived"));
            incomeItem.setAccountId((int)row.get("account_id"));
            incomeItem.setUsersId((int)row.get("users_id"));

            result.add(incomeItem);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access income items assigned to the user
    public List<IncomeItemModel> getAllIncomeItems(final int usersId) {

        final String methodName = "getAllIncomeItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"incomeItem\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<IncomeItemModel> result = new ArrayList<IncomeItemModel>();
        for(Map<String, Object> row:rows){
            IncomeItemModel incomeItem = new IncomeItemModel();
            incomeItem.setId((int)row.get("id"));
            incomeItem.setBudgetIncomeCategoryId((int)row.get("budget_incomeCategory_id"));
            incomeItem.setName((String)row.get("name"));
            incomeItem.setReceivedDate((Timestamp)row.get("receivedDate"));
            incomeItem.setAmountExpected((BigDecimal)row.get("amountExpected"));
            incomeItem.setAmountReceived((BigDecimal)row.get("amountReceived"));
            incomeItem.setAccountId((int)row.get("account_id"));
            incomeItem.setUsersId((int)row.get("users_id"));

            result.add(incomeItem);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access income items assigned to the user
    public List<IncomeItemModel> getIncomeItemById(final int itemId, final int usersId){

        final String methodName = "getIncomeItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"incomeItem\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        IncomeItemModel incomeItem = jdbcTemplate.queryForObject(
                sql, new Object[]{itemId, usersId}, new IncomeItemRowMapper());

        List<IncomeItemModel> result = new ArrayList<IncomeItemModel>();
        result.add(incomeItem);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any periods
    public List<IncomeItemModel> adminGetIncomeItemById(final int itemId){

        final String methodName = "adminGetPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"incomeItem\" WHERE \"id\" = ?;";
        IncomeItemModel incomeItem = jdbcTemplate.queryForObject( sql, new Object[]{itemId}, new IncomeItemRowMapper());

        List<IncomeItemModel> result = new ArrayList<IncomeItemModel>();
        result.add(incomeItem);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income item will be assigned
    //may use this post call
    public List<ReturnIdModel> addIncomeItemReturnId(final IncomeItemModel incomeItem) {

        final String methodName = "addIncomeItemReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"incomeItem\"\n" +
                "\t(\"budget_incomeCategory_id\", \"name\", \"receivedDate\", \n" +
                "\t \"amountExpected\", \"amountReceived\",\"account_id\", \"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?, ?, ?, ?, ?, ?) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, incomeItem.getBudgetIncomeCategoryId());
            ps.setString(2,incomeItem.getName());
            ps.setTimestamp(3, incomeItem.getReceivedDate());
            ps.setBigDecimal(4, incomeItem.getAmountExpected());
            ps.setBigDecimal(5, incomeItem.getAmountReceived());
            ps.setInt(6, incomeItem.getAccountId());
            ps.setInt(7, incomeItem.getUsersId());

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

    //User may only delete expense items assigned to the user
    public boolean deleteIncomeItemById(int itemId, int usersId) {
        final String methodName = "deleteIncomeItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "DELETE FROM \"incomeItem\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        int status = jdbcTemplate.update(sql, itemId, usersId);
        boolean wasDeleted;

        if(status != 0) {
            LOGGER.info(CLASS_NAME + methodName + ": incomeItem with id of " + itemId + " has been deleted successfully.");
            wasDeleted = true;
        }else{
            LOGGER.info(CLASS_NAME + methodName + ": incomeItem with id of " + itemId + " cannot be found, no incomeItem will be deleted.");
            wasDeleted = false;
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return wasDeleted;
    }
}
