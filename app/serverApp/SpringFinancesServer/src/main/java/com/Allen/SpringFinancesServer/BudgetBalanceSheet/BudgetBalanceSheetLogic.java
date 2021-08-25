package com.Allen.SpringFinancesServer.BudgetBalanceSheet;

import com.Allen.SpringFinancesServer.ExpenseItem.ExpenseItemDao;
import com.Allen.SpringFinancesServer.ExpenseItem.ExpenseItemModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class BudgetBalanceSheetLogic {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetBalanceSheetDao dao;

    @Autowired
    ExpenseItemDao expItemDao;

    final static BigDecimal NO_STARTING_AMOUNT = BigDecimal.valueOf(0.00);

    public List<BudgetBalanceSheetModel> balanceSheetByPeriodManager(final int periodId) {

        //Get the target period's budget expense categories
        List <BudgetBalanceSheetModel> budgetBalCatsModelList = dao.getBudgetExpCatsByPeriod(periodId);

        //Get expense items by period
        List <ExpenseItemModel> expItemList = expItemDao.getExpItemByPeriod(periodId);

        //Get merged Budget Balance Sheet List
        List <BudgetBalanceSheetModel> budgetBalanceSheetList = mergeExpenseItemsToExpenseCats(
                budgetBalCatsModelList, expItemList);

        return budgetBalanceSheetList;
    }

    private List<BudgetBalanceSheetModel> mergeExpenseItemsToExpenseCats(
            final List<BudgetBalanceSheetModel> budgetBalCatsModelList, final List<ExpenseItemModel> expItemList){

        //Instantiate new BudgetBalanceSheet List
        List<BudgetBalanceSheetModel> budgetBalanceSheetList = new ArrayList<BudgetBalanceSheetModel>();

        for( BudgetBalanceSheetModel budgetBalCats : budgetBalCatsModelList) {

            List<ExpenseItemModel> newExpenseItemList = new ArrayList<ExpenseItemModel>();
            BudgetBalanceSheetModel newBudgetBalCat = budgetBalCats;

            newBudgetBalCat.setAmountSpent(NO_STARTING_AMOUNT);
            BigDecimal amountSpent = newBudgetBalCat.getAmountSpent();

            for (ExpenseItemModel expItem : expItemList) {


                if(budgetBalCats.getBudgetExpenseCategoryId() == expItem.getBudgetExpenseCategoryId()){
                    ExpenseItemModel newExpItem = expItem;

                    amountSpent = amountSpent.add(expItem.getAmount());

                    newExpenseItemList.add(newExpItem);
                }
            }
            newBudgetBalCat.setAmountSpent(amountSpent);

            BigDecimal amountRemaining = newBudgetBalCat.getAmountBudgeted().subtract(amountSpent);
            newBudgetBalCat.setAmountRemaining(amountRemaining);

            newBudgetBalCat.setExpenseItems( newExpenseItemList );
            budgetBalanceSheetList.add( newBudgetBalCat );
        }

        return budgetBalanceSheetList;
    }

    private BigDecimal getAmountSpent( BigDecimal originalAmount ){

        BigDecimal newAmount = BigDecimal.valueOf(0.00);


        return newAmount;
    }
}
