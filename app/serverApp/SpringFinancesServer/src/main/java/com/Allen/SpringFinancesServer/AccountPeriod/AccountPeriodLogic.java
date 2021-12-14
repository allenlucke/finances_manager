package com.Allen.SpringFinancesServer.AccountPeriod;

import com.Allen.SpringFinancesServer.Account.AccountLogic;
import com.Allen.SpringFinancesServer.Account.AccountModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class AccountPeriodLogic {

    @Autowired
    AccountPeriodDao dao;

    @Autowired
    AccountLogic accountMgr;

    private static final String CLASS_NAME = "AccountPeriodLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    public List<ReturnIdModel> addAcctPeriodReturningId(final AccountPeriodModel acctPeriod){

        final String methodName = "addAcctPeriodReturningId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check to see if account period to be posted would result in a redundancy
        List<AccountPeriodModel> redundantAccountList = getRedundantAccountPeriods(acctPeriod);
        if(redundantAccountList.size() > 0 ) {
            List<ReturnIdModel> emptyReturnedIdList = new ArrayList<>();
            LOGGER.warn(CLASS_NAME + methodName + "This request overlaps/conflicts with a previously existing accountPeriod.");
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return emptyReturnedIdList;
        }
        //Check to validate account dates


        //If not proceed to post account period
        else {
            List<ReturnIdModel> returnedIdList = dao.addAcctPeriodReturningId(acctPeriod);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return returnedIdList;
        }
    }

    //
    //Helper Methods
    //
    public List<AccountPeriodModel> getRedundantAccountPeriods(final AccountPeriodModel acctPeriod){

        final String methodName = "checkForRedundantAccountPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        final int accountId = acctPeriod.getAccountId();
        final int periodId = acctPeriod.getPeriodId();
        final int usersId = acctPeriod.getUsersId();
        List<AccountPeriodModel> redundantAccountList = new ArrayList<>();
        try {
            redundantAccountList = dao.getAccountPeriodByAccountNPeriod(accountId, periodId, usersId);
        }
        catch(EmptyResultDataAccessException e) {

        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return redundantAccountList;
    }

//    public boolean checkForAccountIsOpenByDate(AccountPeriodModel acctPeriod) {
//        final int usersId = acctPeriod.getUsersId();
//        final int acctId = acctPeriod.getAccountId();
//        final int periodId = acctPeriod.getPeriodId();
////        final String date = acctPeriod.get
//
//        List<AccountModel> accountList = accountMgr.getAccountById(acctId, usersId);
//        AccountModel account = accountList.get(0);
//        String acctCreationDate = account.getCreationDate();
//
////        String acctClosingDate = account.getClosingDate();
//
//    }
//
//    checkForAccountIsOpenByDate(final int usersId, final int acctId, final String date) {
//
//    }
    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    //User may only access account periods assigned to the user
    public List<AccountPeriodModel> getAllAccountPeriods(final int usersId) {

        final String methodName = "getAllAccountPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountPeriodModel> result;
        result = dao.getAllAccountPeriods(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all account periods
    public List<AccountPeriodModel> adminGetAllAccountPeriods(){

        final String methodName = "adminGetAllAccountPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountPeriodModel> result;
        result = dao.adminGetAllAccountPeriods();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access account periods assigned to the user
    public List<AccountPeriodModel> getAcctPeriodById(final int acctPeriodId, final int usersId) {

        final String methodName = "getAcctPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountPeriodModel> result;
        result = dao.getAcctPeriodById(acctPeriodId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    public List<AccountPeriodModel> getAccountPeriodByAccountNPeriod(
            final int accountId, final int periodId, final int usersId) throws EmptyResultDataAccessException {

        final String methodName = "getAccountPeriodByAccountNPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountPeriodModel> result;
        result = dao.getAccountPeriodByAccountNPeriod(accountId, periodId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any account period
    public List<AccountPeriodModel> adminGetAcctPeriodById(final int id){

        final String methodName = "adminGetAcctPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountPeriodModel> result;
        result = dao.adminGetAcctPeriodById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

}
