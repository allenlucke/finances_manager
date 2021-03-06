package com.Allen.SpringFinancesServer.Budget;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class BudgetLogic {

    @Autowired
    BudgetDao dao;

    private static final String CLASS_NAME = "BudgetLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    public List<ReturnIdModel> addBudgetReturnId(final BudgetModel budget) {

        final String methodName = "addBudgetReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check to see if the period of the budget to be posted already has a budget assigned to it
        //This would result in an undesired redundancy
        List<BudgetModel> redundantBudgetList = checkForBudgetAlreadyAssignedToPeriod(budget);
        if(redundantBudgetList.size() > 0 ) {
            List<ReturnIdModel> emptyReturnedIdList = new ArrayList<>();
            LOGGER.warn(CLASS_NAME + methodName + "This request overlaps/conflicts with a previously existing budget.");
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return emptyReturnedIdList;
        }
        //If not proceed to post budget
        else {
            List<ReturnIdModel> returnedIdList = dao.addBudgetReturnId(budget);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return returnedIdList;
        }
    }


    public List<BudgetModel> checkForBudgetAlreadyAssignedToPeriod(final BudgetModel budget) {

        final String methodName = "checkForBudgetAlreadyAssignedToPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        final int periodId = budget.getPeriodId();
        final int usersId = budget.getUsersId();

        List<BudgetModel> redundantBudgetList = new ArrayList<>();
        try {
            redundantBudgetList = dao.getBudgetByPeriodId(periodId, usersId);
        }
        catch(EmptyResultDataAccessException e) {

        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return redundantBudgetList;
    }

    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    //User may only access budgets assigned to the user
    public List<BudgetModel> getAllBudgets(final int usersId) {

        final String methodName = "getAllBudgets() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetModel> result;
        result = dao.getAllBudgets(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all budgets
    public List<BudgetModel> adminGetAllBudgets(){

        final String methodName = "adminGetAllBudgets() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetModel> result;
        result = dao.adminGetAllBudgets();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }


    //User may only access budgets assigned to the user
    public List<BudgetModel> getBudgetById(final int budgetId, final int usersId){

        final String methodName = "getBudgetById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetModel> result;
        result = dao.getBudgetById(budgetId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access budgets assigned to the user
    public List<BudgetModel> getBudgetByPeriodId(final int periodId, final int usersId){
        final String methodName = "getBudgetByPeriodId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetModel> result;
        result = dao.getBudgetByPeriodId(periodId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any budget
    public List<BudgetModel> adminGetBudgetById(final int id){

        final String methodName = "adminGetBudgetById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetModel> result;
        result = dao.adminGetBudgetById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

}
