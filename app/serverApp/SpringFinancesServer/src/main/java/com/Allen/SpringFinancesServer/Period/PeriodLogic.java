package com.Allen.SpringFinancesServer.Period;

import com.Allen.SpringFinancesServer.Account.AccountDao;
import com.Allen.SpringFinancesServer.Account.AccountModel;
import com.Allen.SpringFinancesServer.AccountPeriod.AccountPeriodLogic;
import com.Allen.SpringFinancesServer.AccountPeriod.AccountPeriodModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;


@Service
public class PeriodLogic {

    private static final String CLASS_NAME = "PeriodLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    PeriodDao dao;

    @Autowired
    AccountPeriodLogic acctPeriodMgr;

    @Autowired
    AccountDao accountDao;

    public ResponseEntity addPeriodRetId(PeriodModel period, int usersId) {

        final String methodName = "addPeriodRetId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        boolean overlappingPeriodExists = checkForExistingPeriod(period, usersId);

        //Check to see if period to be posted would overlap with an existing period
        if(overlappingPeriodExists){
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            LOGGER.warn(CLASS_NAME + methodName + "This request overlaps/conflicts with a previously existing period.");
            return new ResponseEntity("Bad Request", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        else{
            List<ReturnIdModel> returnedId = dao.addPeriodReturnId(period);

            //Post accountPeriods for new period
            List<ReturnIdModel> newAccountPeriodsIds = postAccountPeriods(returnedId.get(0).getId(), usersId);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

    //Check to determine if a period overlaps with an existing period
    private boolean checkForExistingPeriod(PeriodModel period, int usersId) {

        final String methodName = "checkForExistingPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        int isOverlapping = 0;

        try {
            List<PeriodModel> overlappingPeriods = dao.getOverlappingPeriods(period, usersId);
            isOverlapping = overlappingPeriods.size();
        }
        catch (EmptyResultDataAccessException e){}

        if(isOverlapping > 0) {
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return true;
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return false;
    }

    private List<ReturnIdModel> postAccountPeriods(final int periodId, final int usersId) {

        final String methodName = "postAccountPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> userAccountIdsList = new ArrayList<>();

        //Get List of accounts that match usersId
        List<AccountModel> userAccounts = accountDao.getAllAccounts(usersId);

        for(AccountModel account: userAccounts){
            //Create new account period to post
            AccountPeriodModel newAccountPeriod = new AccountPeriodModel();
            newAccountPeriod.setUsersId(usersId);
            newAccountPeriod.setAccountId(account.getId());
            newAccountPeriod.setPeriodId(periodId);

            //Make post call to account period dao
            List<ReturnIdModel> newAccountPeriodReturnedIdList = acctPeriodMgr.addAcctPeriodReturningId(newAccountPeriod);

            //Add returned ids to userAccountIdsList
            ReturnIdModel returnedId = newAccountPeriodReturnedIdList.get(0);
            userAccountIdsList.add(returnedId);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return userAccountIdsList;
    }
}
