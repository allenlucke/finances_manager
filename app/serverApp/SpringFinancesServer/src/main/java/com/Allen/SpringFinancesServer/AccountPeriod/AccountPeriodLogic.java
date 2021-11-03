package com.Allen.SpringFinancesServer.AccountPeriod;

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
        //If not proceed to post account period
        else {
            List<ReturnIdModel> returnedIdList = dao.addAcctPeriodReturningId(acctPeriod);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return returnedIdList;
        }
    }

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
}
