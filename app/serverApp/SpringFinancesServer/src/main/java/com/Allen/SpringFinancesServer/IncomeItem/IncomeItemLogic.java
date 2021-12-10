package com.Allen.SpringFinancesServer.IncomeItem;

import com.Allen.SpringFinancesServer.Account.AccountDao;
import com.Allen.SpringFinancesServer.Account.AccountModel;
import com.Allen.SpringFinancesServer.BudgetIncomeCategory.BudgetIncomeCategoryDao;
import com.Allen.SpringFinancesServer.BudgetIncomeCategory.BudgetIncomeCategoryModel;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class IncomeItemLogic {

    private static final String CLASS_NAME = "IncomeItemLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    IncomeItemDao dao;

    @Autowired
    AccountDao acctDao;

    @Autowired
    BudgetIncomeCategoryDao budgetIncomeCategoryDao;

    public List<ReturnIdModel> addIncomeItemReturnId(IncomeItemModel inputIncomeItem) {

        final String methodName = "addIncomeItemReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get list of budget expense categories applicable to the desired transaction date
        String desiredTransactionDate = inputIncomeItem.getReceivedDate();
        int usersId = inputIncomeItem.getUsersId();
        List<BudgetIncomeCategoryModel> budgetIncCats = budgetIncomeCategoryDao.getBudgetIncCatsByDate(desiredTransactionDate, usersId);

        List<ReturnIdModel> emptyReturnedIdList = new ArrayList<>();

        //Check to see if account is a credit account, income cannot be posted to a credit account
        boolean isCreditAccount = checkForCreditAccount(inputIncomeItem);
        if(isCreditAccount){
            LOGGER.warn(CLASS_NAME + methodName + " Desired account is a credit account. " +
                    "Income cannot be posted to a credit account.");
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return emptyReturnedIdList;
        }
        //Check to ensure received date matches with an applicable budget income category
        if(budgetIncCats.size() > 0) {
            //Direct to dao
            List<ReturnIdModel> returnedIdList = dao.addIncomeItemReturnId(inputIncomeItem);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return returnedIdList;
        }else {
            LOGGER.warn(CLASS_NAME + methodName + " Desired received date has no matching budget income item categories" +
                    ". The item will not be posted");
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return emptyReturnedIdList;
        }
    }

    private boolean checkForCreditAccount(IncomeItemModel incomeItem){

        final String methodName = "checkForCreditAccount() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        int accountId = incomeItem.getAccountId();
        int usersId = incomeItem.getUsersId();

        List<AccountModel> accountInfoList = acctDao.getAccountById(accountId, usersId);
        boolean isCredit = accountInfoList.get(0).isCredit();
        return isCredit;
    }

}
