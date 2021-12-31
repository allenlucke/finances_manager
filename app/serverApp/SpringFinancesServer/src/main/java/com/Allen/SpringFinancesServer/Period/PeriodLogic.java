package com.Allen.SpringFinancesServer.Period;

import com.Allen.SpringFinancesServer.Account.AccountLogic;
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
    AccountLogic accountMgr;


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

    //
    //Helper Methods
    //

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
        List<AccountModel> userAccounts = accountMgr.getAllAccounts(usersId);

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

    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    //User may only access periods assigned to the user
    //Get a list of periods that no budget has been assigned to
    public List <PeriodModel> getPeriodsWithoutBudget(final int usersId) {

        final String methodName = "getPeriodsWithoutBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Make call to dao
        List <PeriodModel> getPeriodsWithoutBudgetList;
        getPeriodsWithoutBudgetList = dao.getPeriodsWithoutBudget(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return getPeriodsWithoutBudgetList;
    }

    //Admin only, may access all periods
    public List<PeriodModel> adminGetAllPeriods() {

        final String methodName = "adminGetAllPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<PeriodModel> result;
        result = dao.adminGetAllPeriods();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access periods assigned to the user
    public List<PeriodModel> getAllPeriods(final int usersId) {

        final String methodName = "getAllPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<PeriodModel> result;
        result = dao.getAllPeriods(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User only may access current period assigned to user
    public List<PeriodModel> getCurrentPeriod(final int usersId) {

        final String methodName = "getCurrentPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<PeriodModel> result;
        result = dao.getCurrentPeriod(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access accounts assigned to the user
    public List<PeriodModel> getPeriodByDate(final String date, final int usersId){

        final String methodName = "getPeriodByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<PeriodModel> result;
        result = dao.getPeriodByDate(date, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access accounts assigned to the user
    public List<PeriodModel> getPeriodsByDateRange(final String startDate, final String endDate, final int usersId){

        final String methodName = "getPeriodsByDateRange() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<PeriodModel> result;
        result = dao.getPeriodsByDateRange(startDate, endDate, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any periods
    public List<PeriodModel> adminGetPeriodById(final int id) {

        final String methodName = "adminGetPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<PeriodModel> result;
        result = dao.adminGetPeriodById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access periods assigned to the user
    public List<PeriodModel> getPeriodById(final int periodId, final int usersId) {

        final String methodName = "getPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<PeriodModel> result;
        result = dao.getPeriodById(periodId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access periods assigned to the user
    public List<PeriodModel> getOverlappingPeriods(PeriodModel period, final int usersId) throws EmptyResultDataAccessException {

        final String methodName = "getOverlappingPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<PeriodModel> result;
        result = dao.getOverlappingPeriods(period, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the period will be assigned
    //may use this post call
    public List<ReturnIdModel> addPeriodReturnId(final PeriodModel period) {

        final String methodName = "addPeriodReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result;
        result = dao.addPeriodReturnId(period);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

}
