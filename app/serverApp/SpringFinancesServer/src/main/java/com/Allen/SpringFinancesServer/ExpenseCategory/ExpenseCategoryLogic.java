package com.Allen.SpringFinancesServer.ExpenseCategory;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class ExpenseCategoryLogic {

    private static final String CLASS_NAME = "ExpenseCategoryLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    ExpenseCategoryDao dao;

    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    //User may only access expense categories assigned to the user
    public List<ExpenseCategoryModel> getAllExpCats(final int usersId) {

        final String methodName = "getAllExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ExpenseCategoryModel> result;
        result = dao.getAllExpCats(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all expense categories
    public List<ExpenseCategoryModel> adminGetAllExpCats() {

        final String methodName = "adminGetAllExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ExpenseCategoryModel> result;
        result = dao.adminGetAllExpCats();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access expense categories assigned to the user
    public List<ExpenseCategoryModel> getExpCatById(final int catId, final int usersId){

        final String methodName = "getExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ExpenseCategoryModel> result;
        result = dao.getExpCatById(catId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any expense category
    public List<ExpenseCategoryModel> adminGetExpCatById(final int id){

        final String methodName = "adminGetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ExpenseCategoryModel> result;
        result = dao.adminGetExpCatById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Get available expense categories not assigned to the input budget
    //User may only access expense categories assigned to the user
    public List<ExpenseCategoryModel> getExpenseCatsNotAssignedToBudget(final int budgetId, final int usersId) {

        final String methodName = "getExpenseCatsNotAssignedToBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ExpenseCategoryModel> result;
        result = dao.getExpenseCatsNotAssignedToBudget(budgetId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addExpCatReturnId(final ExpenseCategoryModel expCat) {

        final String methodName = "addExpCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result;
        result = dao.addExpCatReturnId(expCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only delete expense categories assigned to the user
    public boolean deleteExpCatById(final int catId, final int usersId) {
        final String methodName = "deleteExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        boolean wasDeleted;
        wasDeleted = dao.deleteExpCatById(catId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return wasDeleted;
    }
}
