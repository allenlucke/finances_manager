package com.Allen.SpringFinancesServer.CloneOrEditBudget;

import com.Allen.SpringFinancesServer.Budget.BudgetDao;
import com.Allen.SpringFinancesServer.Budget.BudgetModel;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryDao;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CloneOrEditBudgetLogic {

    private static final String CLASS_NAME = "AccountBalanceSheetLogic --- ";
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

    public List<BudgetExpenseCategoryModel> cloneBudget(final int templateBudgetId, final int usersId, final BudgetModel newBudget){


        //Post new budget
        List<ReturnIdModel> newBudgetIdList = budgetDao.addBudgetReturnId(newBudget);
        int newBudgetId = newBudgetIdList.get(0).getId();

        //Get List of budget expense categories to be cloned to new budget
        List <BudgetExpenseCategoryModel> matchingExpCats = budgetExpenseCategoryDao.getBudgetExpCatByExpCat(templateBudgetId, usersId);

        //Post matching expense categories to new budget
        List<ReturnIdModel> newExpCatsIdsList = budgetExpenseCategoryDao.addBudgetExpCatReturnId(matchingExpCats.get(0));
        return matchingExpCats;
    }

    private List<ReturnIdModel> postListOfExpenseCats(List<BudgetExpenseCategoryModel> budgetExpCatList){

        List<ReturnIdModel> result = null;
        for(BudgetExpenseCategoryModel budgetExpCat : budgetExpCatList){
            List<ReturnIdModel> returnedIdList= budgetExpenseCategoryDao.addBudgetExpCatReturnId(budgetExpCat);

            result.add(returnedIdList.get(0));
        }
        return  result;
    }
}
