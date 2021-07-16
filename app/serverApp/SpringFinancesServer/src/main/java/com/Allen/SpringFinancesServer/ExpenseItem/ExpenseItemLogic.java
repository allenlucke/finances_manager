package com.Allen.SpringFinancesServer.ExpenseItem;

import com.Allen.SpringFinancesServer.Account.AccountDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExpenseItemLogic {

    @Autowired
    AccountDao acctDao;

    public ExpenseItemModel setPaidWithCredit(ExpenseItemModel expItem){
        //Get accountId from expenseItem
        int acctId = expItem.getAccountId();
        //Check to see if account is credit account
        Boolean isCredit = acctDao.checkForCreditAccount(acctId);
        //Set expenseItem PaidWithCredit
        expItem.setPaidWithCredit(isCredit);

        return expItem;
    }
}
