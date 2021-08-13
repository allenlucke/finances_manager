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

@Service
public class IncomeCategoryDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<IncomeCategoryModel> getAllIncomeCats() {
        String sql = "SELECT * FROM \"incomeCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<IncomeCategoryModel> result = new ArrayList<IncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            IncomeCategoryModel incomeCat = new IncomeCategoryModel();
            incomeCat.setId((int)row.get("id"));
            incomeCat.setName((String)row.get("name"));
            incomeCat.setUsersId((int)row.get("users_id"));

            result.add(incomeCat);
        }
        return result;
    }

    public List<IncomeCategoryModel> getIncomeCatById(int id){
        String sql = "SELECT * FROM \"incomeCategory\" WHERE \"id\" = ?;";

        IncomeCategoryModel incomeCat = jdbcTemplate.queryForObject( sql, new Object[]{id}, new IncomeCategoryRowMapper());

        List<IncomeCategoryModel> result = new ArrayList<IncomeCategoryModel>();

        result.add(incomeCat);

        return result;
    }

    public List<ReturnIdModel> addIncomeCatReturnId(final IncomeCategoryModel incomeCat) {
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

        return result;
    }
}
