package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

import com.Allen.SpringFinancesServer.Model.BudgExpCatRespWithName;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class BudgetExpenseCategoryLogic {

    private static final String CLASS_NAME = "BudgetExpenseCategoryLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    BudgetExpenseCategoryDao dao;

    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    //User may only access budget expense categories assigned to the user
    public List<BudgetExpenseCategoryModel> getAllBudgetExpCats(final int usersId) {

        final String methodName = "getAllBudgetExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetExpenseCategoryModel> result;
        result = dao.getAllBudgetExpCats(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Returns all budget expense categories from the period the date falls within
    //User may only access budget expense categories assigned to the user
    public List<BudgetExpenseCategoryModel> getBudgetExpCatsByDate(final String date, final int usersId) {

        final String methodName = "getBudgetExpCatsByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetExpenseCategoryModel> result;
        result = dao.getBudgetExpCatsByDate(date, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Returns all budget expense categories from the period the date falls within
    //User may only access budget expense categories assigned to the user
    public List<BudgExpCatRespWithName> getBudgetExpCatsWithNameByDate(final String date, final int usersId) {

        final String methodName = "getBudgetExpCatsWithNameByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgExpCatRespWithName> result;
        result = dao.getBudgetExpCatsWithNameByDate(date, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all budget expense categories
    public List<BudgetExpenseCategoryModel> adminGetAllBudgetExpCats() {

        final String methodName = "adminGetAllBudgetExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetExpenseCategoryModel> result;
        result = dao.adminGetAllBudgetExpCats();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access budget expense categories assigned to the user
    public List<BudgetExpenseCategoryModel> getBudgetExpCatById(final int budgExpCatId, final int usersId) {

        final String methodName = "getBudgetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetExpenseCategoryModel> result;
        result = dao.getBudgetExpCatById(budgExpCatId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any budget expense category
    public List<BudgetExpenseCategoryModel> adminGetBudgetExpCatById(final int id){

        final String methodName = "adminGetBudgetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetExpenseCategoryModel> result;
        result = dao.adminGetBudgetExpCatById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addBudgetExpCatReturnId(final BudgetExpenseCategoryModel budgetExpCat) {

        final String methodName = "addBudgetExpCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result;
        result = dao.addBudgetExpCatReturnId(budgetExpCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access budget expense categories assigned to the user
    public List<BudgetExpenseCategoryModel> getBudgetExpCatByExpCat(final int budgetId, final int usersId){

        final String methodName = "getBudgetExpCatByExpCatNUsersId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetExpenseCategoryModel> result;
        result = dao.getBudgetExpCatByExpCat(budgetId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

}
