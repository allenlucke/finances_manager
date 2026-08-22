package com.Allen.SpringFinancesServer.BudgetBalanceSheet;

import com.Allen.SpringFinancesServer.ExpenseItem.ExpenseItemDao;
import com.Allen.SpringFinancesServer.ExpenseItem.ExpenseItemLogic;
import com.Allen.SpringFinancesServer.ExpenseItem.ExpenseItemModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class BudgetBalanceSheetLogic {

    private static final String CLASS_NAME = "BudgetBalanceSheetLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetBalanceSheetDao dao;

    @Autowired
    ExpenseItemLogic expenseItemMgr;

    final static BigDecimal NO_STARTING_AMOUNT = BigDecimal.valueOf(0.00);

    public List<BudgetBalanceSheetModel> balanceSheetByPeriodManager(final int periodId, final int usersId) {

        final String methodName = "balanceSheetByPeriodManager() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the target period's budget expense categories
        LOGGER.info(CLASS_NAME + methodName + ": Getting target period's budget expense categories");
        List <BudgetBalanceSheetModel> budgetBalCatsModelList = dao.getBudgetExpCatsByPeriod(periodId, usersId);

        //Get expense items by period
        LOGGER.info(CLASS_NAME + methodName + ": Getting expense items by period");
        List <ExpenseItemModel> expItemList = expenseItemMgr.getExpItemByPeriod(periodId, usersId);

        //Get merged Budget Balance Sheet List
        LOGGER.info(CLASS_NAME + methodName + ": Getting merged Budget Balance Sheet List");
        List <BudgetBalanceSheetModel> budgetBalanceSheetList = mergeExpenseItemsToExpenseCats(
                budgetBalCatsModelList, expItemList);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return budgetBalanceSheetList;
    }

    private List<BudgetBalanceSheetModel> mergeExpenseItemsToExpenseCats(
            final List<BudgetBalanceSheetModel> budgetBalCatsModelList, final List<ExpenseItemModel> expItemList){

        final String methodName = "mergeExpenseItemsToExpenseCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Instantiate new BudgetBalanceSheet List
        List<BudgetBalanceSheetModel> budgetBalanceSheetList = new ArrayList<BudgetBalanceSheetModel>();

        LOGGER.info(CLASS_NAME + methodName + ": Iterating through budget balance categories list.");
        for( BudgetBalanceSheetModel budgetBalCats : budgetBalCatsModelList) {

            List<ExpenseItemModel> newExpenseItemList = new ArrayList<ExpenseItemModel>();
            BudgetBalanceSheetModel newBudgetBalCat = budgetBalCats;

            //Set initial amount spent to zero
            newBudgetBalCat.setAmountSpent(NO_STARTING_AMOUNT);
            BigDecimal amountSpent = newBudgetBalCat.getAmountSpent();

            LOGGER.info(CLASS_NAME + methodName + ": Iterating through expense item list.");
            for (ExpenseItemModel expItem : expItemList) {

                //Check to see if categories match
                if(budgetBalCats.getBudgetExpenseCategoryId() == expItem.getBudgetExpenseCategoryId()){
                    ExpenseItemModel newExpItem = expItem;

                    //Add expense item amount to amount spent
                    LOGGER.info(CLASS_NAME + methodName + ": Adding expense item amount to amount spent.");
                    amountSpent = amountSpent.add(expItem.getAmount());

                    //Add expense item to new list
                    newExpenseItemList.add(newExpItem);
                }
            }

            //Setting budget balance category's amount spent
            LOGGER.info(CLASS_NAME + methodName + ": Setting budget balance category's amount spent.");
            newBudgetBalCat.setAmountSpent(amountSpent);

            //Setting budget balance category's amount remaining
            LOGGER.info(CLASS_NAME + methodName + ": Setting budget balance category's amount remaining.");
            BigDecimal amountRemaining = newBudgetBalCat.getAmountBudgeted().subtract(amountSpent);
            newBudgetBalCat.setAmountRemaining(amountRemaining);

            //Adding expense item list to budget balance category POJO
            LOGGER.info(CLASS_NAME + methodName + ": Adding expense item list to budget balance category POJO.");
            newBudgetBalCat.setExpenseItems( newExpenseItemList );

            //Adding budget balance category to budget balance category list
            LOGGER.info(CLASS_NAME + methodName + ": Adding budget balance category to budget balance category list.");
            budgetBalanceSheetList.add( newBudgetBalCat );
        }

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return budgetBalanceSheetList;
    }

    private BigDecimal getAmountSpent( BigDecimal originalAmount ){

        BigDecimal newAmount = BigDecimal.valueOf(0.00);
        return newAmount;
    }
}
