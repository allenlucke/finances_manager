package com.Allen.SpringFinancesServer.AccountPeriod;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
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

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class AccountPeriodDao {

    private static final String CLASS_NAME = "AccountPeriodDao --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //User may only access account periods assigned to the user
    protected List<AccountPeriodModel> getAllAccountPeriods(final int usersId){

        final String methodName = "getAllAccountPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"accountPeriod\" WHERE \"users_id\" = ?;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                new Object[] {usersId} );

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<AccountPeriodModel> result = new ArrayList<AccountPeriodModel>();
        for(Map<String, Object> row:rows){
            AccountPeriodModel acctPeriod = new AccountPeriodModel();
            acctPeriod.setId((int)row.get("id"));
            acctPeriod.setAccountId((int)row.get("account_id"));
            acctPeriod.setPeriodId((int)row.get("period_id"));
            acctPeriod.setBeginningBalance((BigDecimal)row.get("beginningBalance"));
            acctPeriod.setEndingBalance((BigDecimal)row.get("endingBalance"));
            acctPeriod.setUsersId((int)row.get("users_id"));

            result.add(acctPeriod);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all account periods
    protected List<AccountPeriodModel> adminGetAllAccountPeriods(){

        final String methodName = "adminGetAllAccountPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"accountPeriod\";";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        LOGGER.info(CLASS_NAME + methodName + "Mapping result set");
        List<AccountPeriodModel> result = new ArrayList<AccountPeriodModel>();
        for(Map<String, Object> row:rows){
            AccountPeriodModel acctPeriod = new AccountPeriodModel();
            acctPeriod.setId((int)row.get("id"));
            acctPeriod.setAccountId((int)row.get("account_id"));
            acctPeriod.setPeriodId((int)row.get("period_id"));
            acctPeriod.setBeginningBalance((BigDecimal)row.get("beginningBalance"));
            acctPeriod.setEndingBalance((BigDecimal)row.get("endingBalance"));
            acctPeriod.setUsersId((int)row.get("users_id"));

            result.add(acctPeriod);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access account periods assigned to the user
    protected List<AccountPeriodModel> getAcctPeriodById(final int acctPeriodId, final int usersId){

        final String methodName = "getAcctPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"accountPeriod\" WHERE \"id\" = ? AND \"users_id\" = ?;";
        AccountPeriodModel acctPeriod = jdbcTemplate.queryForObject(
                sql, new Object[]{acctPeriodId, usersId}, new AccountPeriodRowMapper());

        List<AccountPeriodModel> result = new ArrayList<AccountPeriodModel>();
        result.add(acctPeriod);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access account periods assigned to the user
    protected List<AccountPeriodModel> getAccountPeriodByAccountNPeriod(
            final int accountId, final int periodId, final int usersId) throws EmptyResultDataAccessException {

        final String methodName = "getAccountPeriodByAccountNPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"accountPeriod\" \n" +
                "WHERE \"account_id\" = ? \n" +
                "AND \"period_id\" = ?\n" +
                "AND \"users_id\" = ?;";
        AccountPeriodModel acctPeriod = jdbcTemplate.queryForObject(
                sql, new Object[]{accountId, periodId, usersId}, new AccountPeriodRowMapper());

        List<AccountPeriodModel> result = new ArrayList<AccountPeriodModel>();
        result.add(acctPeriod);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any account period
    protected List<AccountPeriodModel> adminGetAcctPeriodById(final int id){

        final String methodName = "adminGetAcctPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "SELECT * FROM \"accountPeriod\" WHERE \"id\" = ?;";
        AccountPeriodModel acctPeriod = jdbcTemplate.queryForObject(
                sql, new Object[]{id}, new AccountPeriodRowMapper());

        List<AccountPeriodModel> result = new ArrayList<AccountPeriodModel>();
        result.add(acctPeriod);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }


    //Only Admin or the User to whom the account period will be assigned
    //may use this post call
    protected List<ReturnIdModel> addAcctPeriodReturningId(final AccountPeriodModel acctPeriod) {

        final String methodName = "addAcctPeriodReturningId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "INSERT INTO \"accountPeriod\"\n" +
                "\t(\"account_id\", \"period_id\",\t\"users_id\")\n" +
                "VALUES\n" +
                "\t(?, ?, ?);";

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql,
                    Statement.RETURN_GENERATED_KEYS);

            ps.setInt(1, acctPeriod.getAccountId());
            ps.setInt(2, acctPeriod.getPeriodId());
//            ps.setBigDecimal(3, acctPeriod.getBeginningBalance());
            ps.setInt(3, acctPeriod.getUsersId());

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

    //Only Admin or the User to whom the account period will be assigned
    //may use this put call
    protected boolean updateBeginningBalance(final BigDecimal beginningBalance, final int acctPeriodId, final int usersId) {

        final String methodName = "updateBeginningBalance() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        String sql = "UPDATE \"accountPeriod\" SET \"beginningBalance\" = ? \n" +
                "WHERE \"id\" = ? AND \"users_id\" = ?;";
        int status = jdbcTemplate.update(sql, beginningBalance, acctPeriodId, usersId);
        boolean wasUpdated;

        if(status != 0) {
            LOGGER.info(CLASS_NAME + methodName + ": accountPeriod with id of " + acctPeriodId +
                    " has had it's beginning balance updated successfully.");
            wasUpdated = true;
        }else{
            LOGGER.info(CLASS_NAME + methodName + ": accountPeriod with id of " + acctPeriodId +
                    " cannot be found, beginning balance will not be updated.");
            wasUpdated = false;
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return wasUpdated;
    }

}
