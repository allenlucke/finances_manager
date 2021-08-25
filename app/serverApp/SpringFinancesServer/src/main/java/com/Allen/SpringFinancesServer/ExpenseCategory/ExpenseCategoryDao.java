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

@Service
public class ExpenseCategoryDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<ExpenseCategoryModel> getAllExpCats(final int usersId) {
        String sql = "SELECT * FROM \"expenseCategory\" WHERE \"users_id\" =1;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            ExpenseCategoryModel expCat = new ExpenseCategoryModel();
            expCat.setId((int)row.get("id"));
            expCat.setName((String)row.get("name"));
            expCat.setUsersId((int)row.get("users_id"));

            result.add(expCat);
        }
        return result;
    }

    public List<ExpenseCategoryModel> adminGetAllExpCats() {
        String sql = "SELECT * FROM \"expenseCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();
        for(Map<String, Object> row:rows){
            ExpenseCategoryModel expCat = new ExpenseCategoryModel();
            expCat.setId((int)row.get("id"));
            expCat.setName((String)row.get("name"));
            expCat.setUsersId((int)row.get("users_id"));

            result.add(expCat);
        }
        return result;
    }

    public List<ExpenseCategoryModel> getExpCatById(final int catId, final int usersId){
        String sql = "SELECT * FROM \"expenseCategory\" WHERE \"id\" = ? AND \"users_id\" = ?;";

        ExpenseCategoryModel expenseCat = jdbcTemplate.queryForObject( sql, new Object[]{catId, usersId}, new ExpenseCategoryRowMapper());

        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();

        result.add(expenseCat);

        return result;
    }

    public List<ExpenseCategoryModel> adminGetExpCatById(int id){
        String sql = "SELECT * FROM \"expenseCategory\" WHERE \"id\" = ?;";

        ExpenseCategoryModel expenseCat = jdbcTemplate.queryForObject( sql, new Object[]{id}, new ExpenseCategoryRowMapper());

        List<ExpenseCategoryModel> result = new ArrayList<ExpenseCategoryModel>();

        result.add(expenseCat);

        return result;
    }

    public List<ReturnIdModel> addExpCatReturnId(final ExpenseCategoryModel expCat) {
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

        return result;
    }
}
