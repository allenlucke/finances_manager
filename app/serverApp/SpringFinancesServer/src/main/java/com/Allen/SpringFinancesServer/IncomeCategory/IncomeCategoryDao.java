package com.Allen.SpringFinancesServer.IncomeCategory;

import com.Allen.SpringFinancesServer.ExpenseCategory.ExpenseCategoryModel;
import com.Allen.SpringFinancesServer.ExpenseCategory.ExpenseCategoryRowMapper;
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
public class IncomeCategoryDao {

    private static final String CLASS_NAME = "IncomeCategoryDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //User may only access income categories assigned to the user
    public List<IncomeCategoryModel> getAllIncomeCats(final int usersId) {

        final String methodName = "getAllIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"incomeCategory\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<IncomeCategoryModel> result = new ArrayList<IncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            IncomeCategoryModel incomeCat = new IncomeCategoryModel();
            incomeCat.setId((int)row.get("id"));
            incomeCat.setName((String)row.get("name"));
            incomeCat.setUsersId((int)row.get("users_id"));

            result.add(incomeCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all income categories
    public List<IncomeCategoryModel> adminGetAllIncomeCats() {

        final String methodName = "adminGetAllIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"incomeCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<IncomeCategoryModel> result = new ArrayList<IncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            IncomeCategoryModel incomeCat = new IncomeCategoryModel();
            incomeCat.setId((int)row.get("id"));
            incomeCat.setName((String)row.get("name"));
            incomeCat.setUsersId((int)row.get("users_id"));

            result.add(incomeCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any income category
    public List<IncomeCategoryModel> adminGetIncomeCatById(final int id){

        final String methodName = "adminGetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"incomeCategory\" WHERE \"id\" = ?;";
        IncomeCategoryModel incomeCat = jdbcTemplate.queryForObject( sql, new Object[]{id}, new IncomeCategoryRowMapper());

        List<IncomeCategoryModel> result = new ArrayList<IncomeCategoryModel>();
        result.add(incomeCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access income categories assigned to the user
    public List<IncomeCategoryModel> getIncomeCatById(final int id, final int usersId){

        final String methodName = "getIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"incomeCategory\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        IncomeCategoryModel incomeCat = jdbcTemplate.queryForObject(
                sql, new Object[]{id, usersId}, new IncomeCategoryRowMapper());

        List<IncomeCategoryModel> result = new ArrayList<IncomeCategoryModel>();
        result.add(incomeCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addIncomeCatReturnId(final IncomeCategoryModel incomeCat) {

        final String methodName = "addIncomeCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"incomeCategory\"\n" +
                "\t(\"name\", \"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, incomeCat.getName());
            ps.setInt(2, incomeCat.getUsersId());

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
