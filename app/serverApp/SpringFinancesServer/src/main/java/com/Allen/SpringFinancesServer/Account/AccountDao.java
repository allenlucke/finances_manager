package com.Allen.SpringFinancesServer.Account;

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
public class AccountDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<AccountModel> getAllAccounts(final int usersId){
        String sql = "SELECT * FROM \"account\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

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

    public List<AccountModel> adminGetAllAccounts(){
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

    public List<AccountModel> getAccountById(final int acctId, final int usersId){
        String sql = "SELECT * FROM \"account\" WHERE \"id\" = ? AND \"users_id\" = ?;";

        AccountModel account = jdbcTemplate.queryForObject( sql, new Object[]{acctId, usersId}, new AccountRowMapper());

        List<AccountModel> result = new ArrayList<AccountModel>();

        result.add(account);

        return result;
    }

    public List<AccountModel> adminGetAccountById(int id){
        String sql = "SELECT * FROM \"account\" WHERE \"id\" = ?;";

        AccountModel account = jdbcTemplate.queryForObject( sql, new Object[]{id}, new AccountRowMapper());

        List<AccountModel> result = new ArrayList<AccountModel>();

        result.add(account);

        return result;
    }

    public List<ReturnIdModel> addAccountReturningId(final AccountModel acct) {
        String sql = "INSERT INTO \"account\"\n" +
                "\t(\"name\", \"users_id\", \"isCredit\" )\n" +
                "VALUES\n" +
                "\t(?, ?, ?);";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setString(1, acct.getName());
            ps.setInt(2, acct.getUsersId());
            ps.setBoolean(3, acct.isCredit());

            return ps;
        },holder);

        int id = (int) holder.getKey();

        List<ReturnIdModel> result = new ArrayList<ReturnIdModel>();
        ReturnIdModel idObject = new ReturnIdModel();
        idObject.setId(id);

        result.add(idObject);

        return result;
    }

    public boolean checkForCreditAccount(int id){
        String sql = "SELECT \"isCredit\" FROM \"account\" WHERE \"id\" = ?;";

        Boolean isCredit = jdbcTemplate.queryForObject( sql, new Object[]{id}, Boolean.class);


        return isCredit;
    }


}
