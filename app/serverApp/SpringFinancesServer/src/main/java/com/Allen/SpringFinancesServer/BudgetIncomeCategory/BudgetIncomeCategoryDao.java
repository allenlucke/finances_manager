package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

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

@Service
public class BudgetIncomeCategoryDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<BudgetIncomeCategoryModel> getAllBudgetIncomeCats() {
        String sql = "SELECT * FROM \"budget_incomeCategory\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();
        for(Map<String, Object> row:rows){
            BudgetIncomeCategoryModel budgIncomeCat = new BudgetIncomeCategoryModel();
            budgIncomeCat.setId((int)row.get("id"));
            budgIncomeCat.setBudgetId((int)row.get("budget_id"));
            budgIncomeCat.setIncomeCategoryId((int)row.get("incomeCategory_id"));
            budgIncomeCat.setAmountBudgeted((BigDecimal)row.get("amountBudgeted"));

            result.add(budgIncomeCat);
        }
        return result;
    }

    public List<BudgetIncomeCategoryModel> getBudgetIncomeCatById(int id){
        String sql = "SELECT * FROM \"budget_incomeCategory\" WHERE \"id\" = ?;";

        BudgetIncomeCategoryModel budgetIncomeCat = jdbcTemplate.queryForObject( sql, new Object[]{id}, new BudgetIncomeCategoryRowMapper());

        List<BudgetIncomeCategoryModel> result = new ArrayList<BudgetIncomeCategoryModel>();

        result.add(budgetIncomeCat);

        return result;
    }

    public List<ReturnIdModel> addBudgetIncomeCatReturnId(final BudgetIncomeCategoryModel budgetIncomeCat) {
        String sql = "INSERT INTO \"budget_incomeCategory\"\n" +
                "\t(\"budget_id\", \"incomeCategory_id\")\n" +
                "VALUES\n" +
                "\t(?, ?) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, budgetIncomeCat.getBudgetId());
            ps.setInt(2, budgetIncomeCat.getIncomeCategoryId());
//            ps.setBigDecimal(3, budgetExpCat.getAmountBudgeted());

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
