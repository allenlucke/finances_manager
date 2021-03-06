package com.Allen.SpringFinancesServer.Account;

import com.Allen.SpringFinancesServer.AccountPeriod.AccountPeriodLogic;
import com.Allen.SpringFinancesServer.AccountPeriod.AccountPeriodModel;
import com.Allen.SpringFinancesServer.Period.PeriodLogic;
import com.Allen.SpringFinancesServer.Period.PeriodModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import com.Allen.SpringFinancesServer.Utils.TimestampManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class AccountLogic {

    @Autowired
    AccountDao dao;

    @Autowired
    PeriodLogic periodMgr;

    @Autowired
    AccountPeriodLogic acctPeriodMgr;

    private TimestampManager timeMgr = new TimestampManager();

    private static final String CLASS_NAME = "AccountLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    //Only Admin or the User to whom the account will be assigned
    //may use this post call
    public List<ReturnIdModel> addAccountReturningId(final AccountModel acct, final BigDecimal beginningBalance) {

        final String methodName = "addAccountReturningId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result;
        result = dao.addAccountReturningId(acct);

        //Add account periods for all existing periods from account opening date to closing date
        int accountId = result.get(0).getId();
        List<ReturnIdModel> newAccountPeriodIdList;
        newAccountPeriodIdList = addAccountPeriodWhereApplicable(acct, accountId);

        //Add beginning balance to earliest accountPeriod
        int firstAccountPeriodId = newAccountPeriodIdList.get(0).getId();
        boolean hasUpdatedBeginningBalance = updateBeginningBalance(firstAccountPeriodId, acct.getUsersId(), beginningBalance);
        if(!hasUpdatedBeginningBalance){
            LOGGER.warn(CLASS_NAME + methodName + " Beginning balance was not successfully updated. " );
        }

        //Finally return the id of the new account
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //
    //Helper Methods
    //

    //Checks for an existing period that matches account opening date
    public boolean hasApplicablePeriod(final AccountModel acct ) {

        final String methodName = "hasApplicablePeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        int usersId = acct.getUsersId();
        String creationDate = acct.getCreationDate();

        //Get the first period in which the new account is open
        List<PeriodModel> firstPeriodList;
        LOGGER.info(CLASS_NAME + methodName + ": Getting the first period in which the new account is open.");

        try {
            firstPeriodList = periodMgr.getPeriodByDate(creationDate, usersId);
            if(firstPeriodList.size() > 0) {
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                return true;
            }
        }
        catch (EmptyResultDataAccessException e) {
            LOGGER.warn(CLASS_NAME + methodName + " No applicable period exists during account creation date. " );
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return false;
        }
//        if(firstPeriodList.size() > 0) {
//            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
//            return true;
//        }
        LOGGER.warn(CLASS_NAME + methodName + " No applicable period exists during account creation date. " );
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return false;
    }

    //Calls to accountPeriod Manager to update a beginning balance
    public boolean updateBeginningBalance(final int accountPeriodId, final int usersId, final BigDecimal beginningBalance){

        final String methodName = "updateBeginningBalance() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        boolean result = acctPeriodMgr.updateBeginningBalance(beginningBalance, accountPeriodId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Used to automatically create applicable accountPeriods following the creation of an account
    public List<ReturnIdModel> addAccountPeriodWhereApplicable(final AccountModel acct, final int accountId){

        final String methodName = "addAccountPeriodWhereApplicable() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);
        final String defaultClosingDate = "9999-12-31";

        int usersId = acct.getUsersId();
        String creationDate = acct.getCreationDate();
        String closingDate;

        //Get the first period in which the new account is open
        List<PeriodModel> firstPeriodList;
        LOGGER.info(CLASS_NAME + methodName + ": Getting the first period in which the new account is open.");
        firstPeriodList = periodMgr.getPeriodByDate(creationDate, usersId);
        PeriodModel firstPeriod = firstPeriodList.get(0);

        //Check to see if account has a closing date
        //If not use default closing date
        if(acct.getClosingDate() == null || acct.getClosingDate().isBlank()){
            closingDate = defaultClosingDate;
        }
        else {
            closingDate = acct.getClosingDate();
        }

        //Get all other periods in which the account is open
        LOGGER.info(CLASS_NAME + methodName + ": Getting all other periods in which the account is open.");
        List<PeriodModel> allOtherPeriodsList;
        allOtherPeriodsList = periodMgr.getPeriodsByDateRange(creationDate, closingDate, usersId);

        //Combine the two period lists
        List<PeriodModel> allPeriodsList = firstPeriodList;

        for (PeriodModel period : allOtherPeriodsList) {
            allPeriodsList.add(period);
        }

        //Create new account Period list
        List<AccountPeriodModel> accountPeriodsList = new ArrayList<>();
        for(PeriodModel period : allPeriodsList) {
            int periodId = period.getId();
            AccountPeriodModel acctPeriod = new AccountPeriodModel();
            acctPeriod.setPeriodId(periodId);
            acctPeriod.setAccountId(accountId);
            acctPeriod.setUsersId(usersId);
            accountPeriodsList.add(acctPeriod);
        }

        //Call to account period logic class to Post the account periods
        List <ReturnIdModel> newAccountPeriodIdsList = new ArrayList<>();

        for (AccountPeriodModel acctPeriod : accountPeriodsList) {
            List <ReturnIdModel> returnedIdList = acctPeriodMgr.addAcctPeriodReturningId(acctPeriod);
            ReturnIdModel returnedId = returnedIdList.get(0);
            newAccountPeriodIdsList.add(returnedId);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return newAccountPeriodIdsList;
    }

    //Checks to see if account is a credit account
    public boolean checkForCreditAccount(final int id) {

        final String methodName = "checkForCreditAccount() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        boolean isCredit = dao.checkForCreditAccount(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return isCredit;
    }

    //Check to ensure account is open on a desired date
    public boolean checkForAccountIsOpenByDate(final int usersId, final int acctId, final String date) {

        final String methodName = "checkForAccountIsOpenByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountModel> accountList = dao.getAccountById(acctId, usersId);
        Timestamp acctCreateDate = timeMgr.stringToTimestampParser( accountList.get(0).getCreationDate() );
        Timestamp acctClosingDate = timeMgr.stringToTimestampParser( accountList.get(0).getClosingDate() );

        Timestamp desiredDate = timeMgr.stringToTimestampParser( date );

        if( desiredDate.after( acctClosingDate ) ) {
            LOGGER.warn(CLASS_NAME + methodName + " Account has already been closed. " );
            return false;
        }
        if( desiredDate.before( acctCreateDate ) ){
            LOGGER.warn(CLASS_NAME + methodName + " Account has not been created yet. " );
            return false;
        }

        return true;
    }

    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    //User may only access accounts assigned to the user
    public List<AccountModel> getAllAccounts(final int usersId){

        final String methodName = "getAllAccounts() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountModel> allAccountList;
        allAccountList = dao.getAllAccounts(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return allAccountList;
    }

    //Admin only, may access all accounts
    public List<AccountModel> adminGetAllAccounts(){

        final String methodName = "adminGetAllAccounts() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountModel> allAccountList;
        allAccountList = dao.adminGetAllAccounts();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return allAccountList;
    }

    //User may only access accounts assigned to the user
    public List<AccountModel> getAccountById(final int acctId, final int usersId) {

        final String methodName = "getAccountById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountModel> result;
        result = dao.getAccountById(acctId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any account
    public List<AccountModel> adminGetAccountById(final int id) {

        final String methodName = "adminGetAccountById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountModel> result;
        result = dao.adminGetAccountById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }


}
