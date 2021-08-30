package com.Allen.SpringFinancesServer.ExpenseItem;

import com.Allen.SpringFinancesServer.Account.AccountDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class ExpenseItemLogic {

    private static final String CLASS_NAME = "ExpenseItemLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    AccountDao acctDao;

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
