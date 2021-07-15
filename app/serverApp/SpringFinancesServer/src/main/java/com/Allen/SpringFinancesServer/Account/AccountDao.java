package com.Allen.SpringFinancesServer.Account;

import com.Allen.SpringFinancesServer.Budget.BudgetModel;
import com.Allen.SpringFinancesServer.Period.PeriodModel;
import com.Allen.SpringFinancesServer.Period.PeriodRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AccountDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<AccountModel> getAllAccounts(){
        String sql = "SELECT * FROM \"account\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        List<AccountModel> result = new ArrayList<AccountModel>();
        for(Map<String, Object> row:rows){
            AccountModel account = new AccountModel();
            account.setId((int)row.get("id"));
            account.setName((String)row.get("name"));
            account.setUsersId((int)row.get("users_id"));
            account.setCredit((boolean)row.get("isCredit"));
            account.setActive((boolean)row.get("isActive"));

            result.add(account);
        }
        return result;
    }

    public List<AccountModel> getAccountById(int id){
        String sql = "SELECT * FROM \"account\" WHERE \"id\" = ?;";

        AccountModel account = jdbcTemplate.queryForObject( sql, new Object[]{id}, new AccountRowMapper());

        List<AccountModel> result = new ArrayList<AccountModel>();

        result.add(account);

        return result;
    }
}
