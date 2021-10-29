package com.Allen.SpringFinancesServer.ExpenseItem;

import com.Allen.SpringFinancesServer.Account.AccountDao;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryDao;
import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class ExpenseItemLogic {

    private static final String CLASS_NAME = "ExpenseItemLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    ExpenseItemDao dao;

    @Autowired
    AccountDao acctDao;

    @Autowired
    BudgetExpenseCategoryDao budgetExpCatDao;

    public List<ReturnIdModel> addExpItemReturnId(ExpenseItemModel inputExpenseItem){

        final String methodName = "addExpItemReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get list of budget expense categories applicable to the desired transaction date
        String desiredTransactionDate = inputExpenseItem.getTransactionDate();
        int usersId = inputExpenseItem.getUsersId();
        List<BudgetExpenseCategoryModel> budgetExpCats = budgetExpCatDao.getBudgetExpCatsBtDate(desiredTransactionDate, usersId);

        List<ReturnIdModel> emptyReturnedIdList = new ArrayList<>();

        if(budgetExpCats.size() > 0) {
            //Set paid with credit
            ExpenseItemModel updatedExpenseItem = setPaidWithCredit(inputExpenseItem);
            List<ReturnIdModel> returnedIdList = dao.addExpItemReturnId(updatedExpenseItem);

            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return returnedIdList;
        }else {
            LOGGER.warn(CLASS_NAME + methodName + " Desired transaction date has no matching budget expense item categories" +
                    ". The item will not be posted");
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return emptyReturnedIdList;
        }
    }

    public ExpenseItemModel setPaidWithCredit(ExpenseItemModel expItem){

        final String methodName = "setPaidWithCredit() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get accountId from expenseItem
        int acctId = expItem.getAccountId();
        //Check to see if account is credit account
        Boolean isCredit = acctDao.checkForCreditAccount(acctId);
        //Set expenseItem PaidWithCredit
        expItem.setPaidWithCredit(isCredit);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return expItem;
    }
}
