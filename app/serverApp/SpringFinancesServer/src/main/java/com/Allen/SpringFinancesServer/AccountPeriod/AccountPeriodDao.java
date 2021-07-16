package com.Allen.SpringFinancesServer.AccountPeriod;

import com.Allen.SpringFinancesServer.Account.AccountModel;
import com.Allen.SpringFinancesServer.Account.AccountRowMapper;
import com.Allen.SpringFinancesServer.ExpenseCategory.ExpenseCategoryModel;
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
public class AccountPeriodDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<AccountPeriodModel> getAllAccountPeriods(){
        String sql = "SELECT * FROM \"accountPeriod\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<AccountPeriodModel> result = new ArrayList<AccountPeriodModel>();
        for(Map<String, Object> row:rows){
            AccountPeriodModel acctPeriod = new AccountPeriodModel();
            acctPeriod.setId((int)row.get("id"));
            acctPeriod.setAccountId((int)row.get("account_id"));
            acctPeriod.setPeriodId((int)row.get("period_id"));
            acctPeriod.setBeginningBalance((BigDecimal)row.get("beginningBalance"));
            acctPeriod.setEndingBalance((BigDecimal)row.get("endingBalance"));

            result.add(acctPeriod);
        }
        return result;
    }

    public List<AccountPeriodModel> getAcctPeriodById(int id){
        String sql = "SELECT * FROM \"accountPeriod\" WHERE \"id\" = ?;";

        AccountPeriodModel acctPeriod = jdbcTemplate.queryForObject( sql, new Object[]{id}, new AccountPeriodRowMapper());

        List<AccountPeriodModel> result = new ArrayList<AccountPeriodModel>();

        result.add(acctPeriod);

        return result;
    }

    public List<ReturnIdModel> addAcctPeriodReturningId(final AccountPeriodModel acctPeriod) {
        String sql = "INSERT INTO \"accountPeriod\"\n" +
                "\t(\"account_id\", \"period_id\", \"beginningBalance\")\n" +
                "VALUES\n" +
                "\t(?, ?, ?) RETURNING \"id\";";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, acctPeriod.getAccountId());
            ps.setInt(2, acctPeriod.getPeriodId());
            ps.setBigDecimal(3, acctPeriod.getBeginningBalance());

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
