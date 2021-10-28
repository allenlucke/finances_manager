package com.Allen.SpringFinancesServer.CloneOrEditBudget;

import com.Allen.SpringFinancesServer.Budget.BudgetDao;
import com.Allen.SpringFinancesServer.Budget.BudgetModel;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryDao;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryModel;
import com.Allen.SpringFinancesServer.BudgetIncomeCategory.BudgetIncomeCategoryDao;
import com.Allen.SpringFinancesServer.BudgetIncomeCategory.BudgetIncomeCategoryModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class CloneOrEditBudgetLogic {

    private static final String CLASS_NAME = "CloneOrEditBudgetLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

//    @Autowired
//    private CloneOrEditBudgetDao dao;

    @Autowired
    BudgetDao budgetDao;

    @Autowired
    private BudgetExpenseCategoryDao budgetExpenseCategoryDao;

    @Autowired
    private BudgetIncomeCategoryDao budgetIncomeCategoryDao;

    //Method manages cloning of a budget
    public List<ReturnIdModel> cloneBudget(final int templateBudgetId, final int usersId, final BudgetModel newBudget){

        final String methodName = "cloneBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Post new budget
        List<ReturnIdModel> newBudgetIdList = budgetDao.addBudgetReturnId(newBudget);
        int newBudgetId = newBudgetIdList.get(0).getId();

        //Get List of budget expense categories to be cloned to new budget
        List<BudgetExpenseCategoryModel> matchingBudgetExpCatsList = budgetExpenseCategoryDao.getBudgetExpCatByExpCat(templateBudgetId, usersId);

        //Replace template budget id in matchingBudgetExpCatsList with newBudgetId
        List<BudgetExpenseCategoryModel> updatedBudgetExpCatsList = replaceBudgetIdExpCats(matchingBudgetExpCatsList, newBudgetId);

        //Post budget expense categories to new budget
        List<ReturnIdModel> newExpCatsIdsList = postListOfExpenseCats(updatedBudgetExpCatsList);

        //Get List of budget income categories to be cloned to new budget
        List<BudgetIncomeCategoryModel> matchingBudgetIncCatsList = budgetIncomeCategoryDao.getBudgetIncCatByExpCat(templateBudgetId, usersId);

        //Replace template budget id in matchingBudgetIncCatsList with newBudgetId
        List<BudgetIncomeCategoryModel> updatedIncCatsList = replaceBudgetIdIncCats(matchingBudgetIncCatsList, newBudgetId);

        //Post budget income categories to new budget
        List<ReturnIdModel> newIncCatsIdsList = postListOfIncomeCats(updatedIncCatsList);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return newBudgetIdList;
    }

    //Iterates through list of budget expense categories, replaces budget id with new budget id
    private List<BudgetExpenseCategoryModel> replaceBudgetIdExpCats(List<BudgetExpenseCategoryModel> budgetExpCatsList, int targetBudgetId){

        final String methodName = "replaceBudgetIdExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetExpenseCategoryModel> updatedBudgetExpCatsList = new ArrayList<>();
        for(BudgetExpenseCategoryModel budgetExpCat : budgetExpCatsList){

            LOGGER.info(CLASS_NAME + methodName + " budgetExpCatId: " + budgetExpCat.getId());
            budgetExpCat.setBudgetId(targetBudgetId);
            updatedBudgetExpCatsList.add(budgetExpCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return updatedBudgetExpCatsList;
    }

    //Iterates through list of budget income categories, replaces budget id with new budget id
    private List<BudgetIncomeCategoryModel> replaceBudgetIdIncCats(List<BudgetIncomeCategoryModel> budgetIncCatsList, int targetBudgetId){

        final String methodName = "replaceBudgetIdIncCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<BudgetIncomeCategoryModel> updatedBudgetIncCatsList = new ArrayList<>();
        for(BudgetIncomeCategoryModel budgetIncCat : budgetIncCatsList){

            budgetIncCat.setBudgetId(targetBudgetId);
            updatedBudgetIncCatsList.add(budgetIncCat);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return updatedBudgetIncCatsList;
    }

    //Iterates through list of budget expense categories, posts each one and returns list
    //of successfully posted ids
    private List<ReturnIdModel> postListOfExpenseCats(List<BudgetExpenseCategoryModel> budgetExpCatList){

        final String methodName = "postListOfExpenseCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result = new ArrayList<>();
        for(BudgetExpenseCategoryModel budgetExpCat : budgetExpCatList){
            List<ReturnIdModel> returnedIdList= budgetExpenseCategoryDao.addBudgetExpCatReturnId(budgetExpCat);

            result.add(returnedIdList.get(0));
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return  result;
    }

    //Iterates through list of budget income categories, posts each one and returns list
    //of successfully posted ids
    private List<ReturnIdModel> postListOfIncomeCats(List<BudgetIncomeCategoryModel> budgetIncCatList){

        final String methodName = "postListOfIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<ReturnIdModel> result = new ArrayList<>();
        for(BudgetIncomeCategoryModel budgetIncCat : budgetIncCatList){
            List<ReturnIdModel> returnedIdList= budgetIncomeCategoryDao.addBudgetIncomeCatReturnId(budgetIncCat);

            result.add(returnedIdList.get(0));
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return  result;
    }
}
