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

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class AccountDao {

    private static final String CLASS_NAME = "AccountDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //User may only access accounts assigned to the user
    public List<AccountModel> getAllAccounts(final int usersId){

        final String methodName = "etAllAccounts() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"account\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
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
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all accounts
    public List<AccountModel> adminGetAllAccounts(){

        final String methodName = "adminGetAllAccounts() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"account\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
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
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access accounts assigned to the user
    public List<AccountModel> getAccountById(final int acctId, final int usersId){

        final String methodName = "getAccountById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"account\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        AccountModel account = jdbcTemplate.queryForObject(
                sql, new Object[]{acctId, usersId}, new AccountRowMapper());

        List<AccountModel> result = new ArrayList<AccountModel>();
        result.add(account);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }
    //Admin only, may access any account
    public List<AccountModel> adminGetAccountById(int id){

        final String methodName = "adminGetAccountById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"account\" WHERE \"id\" = ?;";
        AccountModel account = jdbcTemplate.queryForObject( sql, new Object[]{id}, new AccountRowMapper());

        List<AccountModel> result = new ArrayList<AccountModel>();
        result.add(account);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the account period will be assigned
    //may use this post call
    public List<ReturnIdModel> addAccountReturningId(final AccountModel acct) {

        final String methodName = "addAccountReturningId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

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

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Used by ExpenseItemLogic, no auth checks as user will have already passed auth check
    public boolean checkForCreditAccount(int id){

        final String methodName = "checkForCreditAccount() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT \"isCredit\" FROM \"account\" WHERE \"id\" = ?;";
        Boolean isCredit = jdbcTemplate.queryForObject( sql, new Object[]{id}, Boolean.class);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return isCredit;
    }


}
