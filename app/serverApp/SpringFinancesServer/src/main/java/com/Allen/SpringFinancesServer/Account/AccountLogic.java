package com.Allen.SpringFinancesServer.Account;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import com.Allen.SpringFinancesServer.Utils.TimestampManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class AccountLogic {

    @Autowired
    AccountDao dao;

    private TimestampManager timeMgr = new TimestampManager();

    private static final String CLASS_NAME = "AccountLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";


    public boolean checkForCreditAccount(final int id) {

        final String methodName = "checkForCreditAccount() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        boolean isCredit = dao.checkForCreditAccount(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return isCredit;
    }

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
    //*** DAO Calls ***//
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

    //Only Admin or the User to whom the account period will be assigned
    //may use this post call
    public List<ReturnIdModel> addAccountReturningId(final AccountModel acct) {

        final String methodName = "addAccountReturningId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result;
        result = dao.addAccountReturningId(acct);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

}
