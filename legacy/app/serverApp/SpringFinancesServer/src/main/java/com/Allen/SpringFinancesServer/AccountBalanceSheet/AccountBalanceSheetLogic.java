package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import com.Allen.SpringFinancesServer.AccountPeriod.AccountPeriodLogic;
import com.Allen.SpringFinancesServer.AccountPeriod.AccountPeriodModel;
import com.Allen.SpringFinancesServer.Period.PeriodDao;
import com.Allen.SpringFinancesServer.Period.PeriodLogic;
import com.Allen.SpringFinancesServer.Period.PeriodModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@Service
public class AccountBalanceSheetLogic {

    private static final String CLASS_NAME = "AccountBalanceSheetLogic --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    AccountBalanceSheetDao dao;

    @Autowired
    PeriodLogic periodMgr;

    @Autowired
    AccountPeriodLogic accountPeriodMgr;

    final BigDecimal NO_BEGINNING_BALANCE = BigDecimal.valueOf(0.00);
    private BigDecimal beginningBalance;

    public List<AccountBalanceSheetModel> accountBalanceSheetManager(final int acctId, final int periodId, final int usersId){

        final String methodName = "accountBalanceSheetManager() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get accountPeriodId for desired balance sheet
        final int accountPeriodId = getAccountPeriodId(acctId, periodId, usersId);

        //Get expense item data for desired period
        LOGGER.info(CLASS_NAME + methodName + ": Getting expense item data for desired period.");
        List<AccountBalanceSheetModel>  desiredPeriodExpenseData =
                dao.getExpItemByPeriodNAcctType(acctId, periodId, usersId);

        for( AccountBalanceSheetModel exp : desiredPeriodExpenseData) {
            LOGGER.info(CLASS_NAME + methodName + ": Desired period exp amount: " + exp.getAmount());
        }

        //Get income item data for desired period
        LOGGER.info(CLASS_NAME + methodName + ": Getting income item data for desired period.");
        List<AccountBalanceSheetModel>  desiredPeriodIncomeData =
                dao.getIncomeItemByPeriodNAcctType(acctId, periodId, usersId);

        for( AccountBalanceSheetModel income : desiredPeriodIncomeData) {
            LOGGER.info(CLASS_NAME + methodName + ": Desired period income amount: " + income.getAmount());
        }

        //Get startDate of desiredPeriod
        LOGGER.info(CLASS_NAME + methodName + ": Getting startDate of desired period.");
        List<PeriodModel> desiredPeriod = periodMgr.getPeriodById(periodId, usersId);
        final String desiredPeriodStartDate = desiredPeriod.get(0).getStartDate();
        LOGGER.info(CLASS_NAME + methodName + ": Desired period startDate: " + desiredPeriodStartDate);

        int lastUnclosedPeriod = 0;

        //Check to see if the desired period has a beginning balance
        LOGGER.info(CLASS_NAME + methodName + ": Checking to see if the desired account period has a beginning balance.");
        if(!getBeginningBalance(accountPeriodId, usersId).equals(NO_BEGINNING_BALANCE)){
            beginningBalance = getBeginningBalance(accountPeriodId, usersId);
            LOGGER.info(CLASS_NAME + methodName +
                    ": DesiredPeriod has a beginning balance, beginning balance is: " + beginningBalance);

            //Get Balance Sheet
            LOGGER.info(CLASS_NAME + methodName + ": Getting Account Balance Sheet.");
            List<AccountBalanceSheetModel> response =
                    generateBalanceSheet(beginningBalance, desiredPeriodExpenseData, desiredPeriodIncomeData);

            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return response;
        }

        //If desired period does not have a beginning balance
        //get the beginning balance from the oldest unclosed period
        else {
            //Get data for oldest unclosed period
            LOGGER.info(CLASS_NAME + methodName + ": Getting data for oldest unclosed period.");
            OldestUnclosedPeriodModel oldestUnclosedPeriod = getOldestUnclosedPeriod(desiredPeriodStartDate, usersId);

            //Get startdate for oldest unclosed period
            LOGGER.info(CLASS_NAME + methodName + ": Getting startdate for oldest unclosed period.");
            String oldestUnclosedPerStartDate = oldestUnclosedPeriod.getStartDate();
            LOGGER.info(CLASS_NAME + methodName +
                    ": Startdate of oldest unclosed period is: " + oldestUnclosedPerStartDate);

            //Get ID for oldest unclosed period
            LOGGER.info(CLASS_NAME + methodName + ": Getting ID for oldest unclosed period.");
            int oldestUnclosedPeriodId = oldestUnclosedPeriod.getPeriodId();

            //Get accountPeriodId for oldest unclosed period
            LOGGER.info(CLASS_NAME + methodName + ": Getting accountPeriodId for oldest unclosed period.");
            int oldestUnclosedAccountPeriodId = getAccountPeriodId(acctId, oldestUnclosedPeriodId, usersId);

            //Get beginning balance for oldest unclosed period
            LOGGER.info(CLASS_NAME + methodName + ": Getting beginning balance for oldest unclosed period.");
            BigDecimal oldestUnclosedPerBegBal = getBeginningBalance(oldestUnclosedAccountPeriodId, usersId);
            LOGGER.info(CLASS_NAME + methodName +
                    ": Beginning balance of oldest unclosed period is: " + oldestUnclosedPerBegBal);

            //Get expense and income items from start of oldest unclosed period up to day prior to desired period
            LOGGER.info(CLASS_NAME + methodName +
                    ": Getting expense items from start of oldest unclosed period up to day prior to desired period.");
            List<AccountBalanceSheetModel> expItemsPriorToDesiredPeriodList =
                    dao.getExpItemByDatesNAcctType(
                            acctId, oldestUnclosedPerStartDate, desiredPeriodStartDate, usersId);

            //Get income items from start of oldest unclosed period up to day prior to desired period
            LOGGER.info(CLASS_NAME + methodName +
                    ": Getting income items from start of oldest unclosed period up to day prior to desired period.");
            List<AccountBalanceSheetModel> incomeItemsPriorToDesiredPeriodList =
                    dao.getIncomeItemByDatesNAcctType(
                            acctId, oldestUnclosedPerStartDate, desiredPeriodStartDate, usersId);

            //Get desired period's starting balance
            LOGGER.info(CLASS_NAME + methodName + ": Getting desired period's starting balance.");
            beginningBalance = getEndingBalance(
                    oldestUnclosedPerBegBal, expItemsPriorToDesiredPeriodList, incomeItemsPriorToDesiredPeriodList);
            LOGGER.info(CLASS_NAME + methodName + ": Desired period beginning Balance: " + beginningBalance);

            //Get Account Balance Sheet
            LOGGER.info(CLASS_NAME + methodName + ": Getting Account Balance Sheet.");
            List<AccountBalanceSheetModel> response =
                    generateBalanceSheet(beginningBalance, desiredPeriodExpenseData, desiredPeriodIncomeData);

            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return response;
        }
    }

    //Get beginning balance of a period
    private BigDecimal getBeginningBalance(final int acctPeriodId, final int usersId) {

        final String methodName = "getBeginningBalance() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        try {
            //Check desired period for beginning balance
            LOGGER.info(CLASS_NAME + methodName + ": Checking desired period for beginning balance.");
            List<AccountPeriodModel> beginningBalanceList = accountPeriodMgr.getAcctPeriodById(acctPeriodId, usersId);
            BigDecimal beginningBalance = beginningBalanceList.get(0).getBeginningBalance();

            LOGGER.info(CLASS_NAME + methodName + ": Beginning Balance : " + beginningBalance);

            //Check to see if beginning balance is null
            if(beginningBalance != null){
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                return beginningBalance;
            }
            //If if beginning balance is null, return beginning balance of 0.00
            else {
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                return NO_BEGINNING_BALANCE;
            }
        }
        catch (EmptyResultDataAccessException e) {
            LOGGER.info(CLASS_NAME + methodName + ": Beginning Balance : " + beginningBalance);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return NO_BEGINNING_BALANCE;
        }
    }

    //Get data of oldest unclosed period
    private OldestUnclosedPeriodModel getOldestUnclosedPeriod(String desiredPeriodStartDate, final int usersId) {

        final String methodName = "getOldestUnclosedPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        OldestUnclosedPeriodModel oldestUnclosedPeriodList = new OldestUnclosedPeriodModel();
        try {
            //Get data of oldest unclosed period
            LOGGER.info(CLASS_NAME + methodName + ": Getting data of oldest unclosed period.");
            oldestUnclosedPeriodList = dao.getLastUnclosedPeriod(desiredPeriodStartDate, usersId);
            int oldestUnclosedPeriod = oldestUnclosedPeriodList.getPeriodId();

            LOGGER.info(CLASS_NAME + methodName + ": Last unclosed period id: " + oldestUnclosedPeriod);
            LOGGER.info(CLASS_NAME + methodName +
                    ": Last unclosed period start date: " + oldestUnclosedPeriodList.getStartDate());
            return oldestUnclosedPeriodList;
        } catch (EmptyResultDataAccessException e) {
            LOGGER.warn(CLASS_NAME + methodName + " No data returned for last unclosed period.");
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return oldestUnclosedPeriodList;
    }

    //Calculate ending balance of a period
    //To be used to determine starting balance of next period
    private BigDecimal getEndingBalance(BigDecimal startingBalance,
                                        List<AccountBalanceSheetModel> expenseItemList,
                                        List<AccountBalanceSheetModel> incomeItemList){

        final String methodName = "getEndingBalance() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Merge and order expense item and income item lists
        LOGGER.info(CLASS_NAME + methodName + ": Getting data of oldest unclosed period.");
        List<AccountBalanceSheetModel> orderedBalanceSheetList =
                mergeAndOrderBalanceListByDate(expenseItemList, incomeItemList);

        for( AccountBalanceSheetModel balanceItem : orderedBalanceSheetList) {
            LOGGER.info(CLASS_NAME + methodName + ": Balance: " +  startingBalance);
            LOGGER.info(CLASS_NAME + methodName + ": Balance item to String; " + balanceItem.toString());
            //Check to see if balance item has income id
            if(balanceItem.getIncomeItemId() == 0) {
                LOGGER.info(CLASS_NAME + methodName + ": Expense amount: " + balanceItem.getAmount());

                startingBalance = startingBalance.subtract(balanceItem.getAmount());
                LOGGER.info(CLASS_NAME + methodName + ": New Balance: " + startingBalance);
            }
            //If not must be an expense item
            else {
                LOGGER.info(CLASS_NAME + methodName + ": Income amount: " + balanceItem.getAmount());

                startingBalance = startingBalance.add(balanceItem.getAmount());
                LOGGER.info(CLASS_NAME + methodName + ": New Balance: " + startingBalance);
            }
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return startingBalance;
    }

    //Calculate balance sheet based on expense and income Items
    private List<AccountBalanceSheetModel> generateBalanceSheet(BigDecimal startingBalance,
                                                                List<AccountBalanceSheetModel> expenseItemList,
                                                                List<AccountBalanceSheetModel> incomeItemList){

        final String methodName = "generateBalanceSheet() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Merge and order expense item and income item lists
        LOGGER.info(CLASS_NAME + methodName + ": Merging and ordering expense item and income item lists.");
        List<AccountBalanceSheetModel> orderedBalanceSheetList =
                mergeAndOrderBalanceListByDate(expenseItemList, incomeItemList);

        List<AccountBalanceSheetModel>  result = new ArrayList<AccountBalanceSheetModel>();;
        for( AccountBalanceSheetModel balanceItem : orderedBalanceSheetList) {

            LOGGER.info(CLASS_NAME + methodName + ": Balance: " +  startingBalance);
            balanceItem.setPreBalance(startingBalance);
            //Check to see if balance item has income id
            if(balanceItem.getIncomeItemId() == 0) {
                LOGGER.info(CLASS_NAME + methodName + ": Expense amount: " + balanceItem.getAmount());

                startingBalance = startingBalance.subtract(balanceItem.getAmount());
                LOGGER.info(CLASS_NAME + methodName + ": New Balance: " + startingBalance);
                balanceItem.setPostBalance(startingBalance);
            }
            //If not must be an expense item
            else {
                LOGGER.info(CLASS_NAME + methodName + ": Income amount: " + balanceItem.getAmount());

                startingBalance = startingBalance.add(balanceItem.getAmount());
                LOGGER.info(CLASS_NAME + methodName + ": New Balance: " + startingBalance);
                balanceItem.setPostBalance(startingBalance);
            }
            result.add(balanceItem);
        }
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Merge and order expense list and income list into on ordered list
    private List<AccountBalanceSheetModel> mergeAndOrderBalanceListByDate(
            List<AccountBalanceSheetModel> expenseItemList, List<AccountBalanceSheetModel> incomeItemList){

        final String methodName = "mergeAndOrderBalanceListByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Merge expense item and income item lists
        LOGGER.info(CLASS_NAME + methodName + ": Merging expense item and income item lists.");
        List<AccountBalanceSheetModel> unorderedBalanceSheetList = new ArrayList<AccountBalanceSheetModel>(expenseItemList);
        unorderedBalanceSheetList.addAll(incomeItemList);

        //Sort expense item and income item lists by date
        LOGGER.info(CLASS_NAME + methodName + ": Sorting expense item and income item lists by date.");
        Collections.sort(unorderedBalanceSheetList, new SortAccountBalanceSheet());

        List<AccountBalanceSheetModel> orderedBalanceSheetList = new ArrayList<AccountBalanceSheetModel>(unorderedBalanceSheetList);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return orderedBalanceSheetList;
    }

    private int getAccountPeriodId(int accountId, int periodId, int usersId){
        final String methodName = "getAccountPeriodId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        List<AccountPeriodModel> accountPeriod = accountPeriodMgr.getAccountPeriodByAccountNPeriod(accountId, periodId, usersId);
        int accountPeriodId = accountPeriod.get(0).getId();

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return accountPeriodId;
    }
}
