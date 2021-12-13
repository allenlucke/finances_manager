package com.Allen.SpringFinancesServer.ExpenseItem;

import com.Allen.SpringFinancesServer.Account.AccountLogic;
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
    AccountLogic accountMgr;

    @Autowired
    BudgetExpenseCategoryDao budgetExpCatDao;

    public List<ReturnIdModel> addExpItemReturnId(ExpenseItemModel inputExpenseItem){

        final String methodName = "addExpItemReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get list of budget expense categories applicable to the desired transaction date
        String desiredTransactionDate = inputExpenseItem.getTransactionDate();
        int usersId = inputExpenseItem.getUsersId();
        List<BudgetExpenseCategoryModel> budgetExpCats = budgetExpCatDao.getBudgetExpCatsByDate(desiredTransactionDate, usersId);

        List<ReturnIdModel> emptyReturnedIdList = new ArrayList<>();

        //Check to ensure account is active as of the received date
        boolean checkForAccountIsOpenByDate = accountMgr.checkForAccountIsOpenByDate(
                inputExpenseItem.getUsersId(),
                inputExpenseItem.getAccountId(),
                inputExpenseItem.getTransactionDate()
        );

        if(!checkForAccountIsOpenByDate){
            LOGGER.warn(CLASS_NAME + methodName + " Desired account is not active as of:  " + inputExpenseItem.getTransactionDate());
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return emptyReturnedIdList;
        }
        //Check to ensure received date matches with an applicable budget income category
        if(budgetExpCats.size() > 0) {
            //Set paid with credit
            ExpenseItemModel updatedExpenseItem = setPaidWithCredit(inputExpenseItem);
            //Direct to correct dao
            List<ReturnIdModel> returnedIdList = addExpItemDaoDirector(updatedExpenseItem);

            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return returnedIdList;
        }else {
            LOGGER.warn(CLASS_NAME + methodName + " Desired transaction date has no matching budget expense item categories" +
                    ". The item will not be posted");
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return emptyReturnedIdList;
        }
    }

    private ExpenseItemModel setPaidWithCredit(ExpenseItemModel expItem){

        final String methodName = "setPaidWithCredit() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get accountId from expenseItem
        int acctId = expItem.getAccountId();
        //Check to see if account is credit account
        Boolean isCredit = accountMgr.checkForCreditAccount(acctId);
        //Set expenseItem PaidWithCredit
        expItem.setPaidWithCredit(isCredit);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return expItem;
    }

    private List<ReturnIdModel> addExpItemDaoDirector(ExpenseItemModel expenseItem) {

        final String methodName = "addExpItemDaoDirector() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //If expense item is a payment to a credit card account
        if (expenseItem.getPaymentToCreditAccount() || expenseItem.getInterestPaymentToCreditAccount()) {
            List<ReturnIdModel> returnedIdList = dao.addExpItemReturnIdPaymentToCredit(expenseItem);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return returnedIdList;
        } else {
            List<ReturnIdModel> returnedIdList = dao.addExpItemReturnId(expenseItem);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return returnedIdList;
        }
    }
}
