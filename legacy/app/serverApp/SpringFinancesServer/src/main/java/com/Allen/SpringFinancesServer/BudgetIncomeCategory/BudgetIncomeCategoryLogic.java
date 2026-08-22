package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

import com.Allen.SpringFinancesServer.Model.BudgIncCatRespWithName;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class BudgetIncomeCategoryLogic {

    private static final String CLASS_NAME = "BudgetIncomeCategoryLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    BudgetIncomeCategoryDao dao;

    //***
    //*** Basic DAO Calls - No Logic Required ***//
    //***

    //User may only access budget income categories assigned to the user
    public List<BudgetIncomeCategoryModel> getAllBudgetIncomeCats(final int usersId) {

        final String methodName = "getAllBudgetIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetIncomeCategoryModel> result;
        result = dao.getAllBudgetIncomeCats(usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access all budget income categories
    public List<BudgetIncomeCategoryModel> adminGetAllBudgetIncomeCats() {

        final String methodName = "adminGetAllBudgetIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetIncomeCategoryModel> result;
        result = dao.adminGetAllBudgetIncomeCats();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access budget income categories assigned to the user
    public List<BudgetIncomeCategoryModel> getBudgetIncomeCatById(final int budgIncCatId, final int usersId){

        final String methodName = "getBudgetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetIncomeCategoryModel> result;
        result = dao.getBudgetIncomeCatById(budgIncCatId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only, may access any budget income category
    public List<BudgetIncomeCategoryModel> adminGetBudgetIncomeCatById(final int id) {

        final String methodName = "adminGetBudgetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetIncomeCategoryModel> result;
        result = dao.adminGetBudgetIncomeCatById(id);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Only Admin or the User to whom the income category will be assigned
    //may use this post call
    public List<ReturnIdModel> addBudgetIncomeCatReturnId(final BudgetIncomeCategoryModel budgetIncomeCat) {

        final String methodName = "addBudgetIncomeCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result;
        result = dao.addBudgetIncomeCatReturnId(budgetIncomeCat);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //User may only access budget expense categories assigned to the user
    public List<BudgetIncomeCategoryModel> getBudgetIncCatByIncCat(final int budgetId, final int usersId){

        final String methodName = "getBudgetIncCatByIncCat() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetIncomeCategoryModel> result;
        result = dao.getBudgetIncCatByIncCat(budgetId, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Returns all budget income categories from the period the date falls within
    //User may only access budget income categories assigned to the user
    public List<BudgetIncomeCategoryModel> getBudgetIncCatsByDate(final String date, final int usersId) {

        final String methodName = "getBudgetIncCatsByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetIncomeCategoryModel> result;
        result = dao.getBudgetIncCatsByDate(date, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Returns all budget income categories from the period the date falls within
    //User may only access budget income categories assigned to the user
    public List<BudgIncCatRespWithName> getBudgetIncCatsWithNameByDate(final String date, final int usersId) {

        final String methodName = "getBudgetIncCatsWithNameByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgIncCatRespWithName> result;
        result = dao.getBudgetIncCatsWithNameByDate(date, usersId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }
    
}
