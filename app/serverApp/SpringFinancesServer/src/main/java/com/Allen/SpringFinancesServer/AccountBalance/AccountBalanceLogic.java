package com.Allen.SpringFinancesServer.AccountBalance;

import com.Allen.SpringFinancesServer.AccountPeriod.AccountPeriodDao;
import com.Allen.SpringFinancesServer.AccountPeriod.AccountPeriodModel;
import com.Allen.SpringFinancesServer.Period.PeriodDao;
import com.Allen.SpringFinancesServer.Period.PeriodModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
public class AccountBalanceLogic {
//    final int noPriorPeriod = 0;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountBalanceDao acctBalDao;

    @Autowired
    PeriodDao periodDao;

    @Autowired
    AccountPeriodDao acctPeriodDao;

    final BigDecimal NO_BEGINNING_BALANCE = BigDecimal.valueOf(0.00);
    private BigDecimal beginningBalance;

    public List<BalanceSheetModel> balanceManager(final int acctId, final int periodId){

        //
        //Get expense item data for desired period
        //
        List<BalanceSheetModel>  desiredPeriodData = acctBalDao.getExpItemByPeriodNAcctType(acctId, periodId);

        for( BalanceSheetModel exp : desiredPeriodData) {
            System.out.println("Desired period exp amount: " + exp.getAmount());
        }

        //
        //Get startDate of desiredPeriod
        //
        List<PeriodModel> desiredPeriod = periodDao.getPeriodById(periodId);
        final Timestamp desiredPeriodStartDate = desiredPeriod.get(0).getStartDate();

        System.out.println("Desired period startDate: " + desiredPeriodStartDate);

        int lastUnclosedPeriod = 0;

        //
        //Check to see if the desired period has a beginning balance
        //
        if(!getBeginningBalance(periodId).equals(NO_BEGINNING_BALANCE)){
            beginningBalance = getBeginningBalance(periodId);
            System.out.println("DesiredPeriod has a beginning balance, beginning balance is: " + beginningBalance);
            //Get Balance Sheet
            List<BalanceSheetModel> response = generateBalanceSheet(beginningBalance, desiredPeriodData);
            return response;
        }
        //
        //If Not get the beginning balance from the oldest unclosed period
        //
        else {
            //Get data for oldest unclosed period
            OldestUnclosedPeriodModel oldestUnclosedPeriod = getOldestUnclosedPeriod(desiredPeriodStartDate);
            //Get startdate for oldest unclosed period
            Timestamp oldestUnclosedPerStartDate = oldestUnclosedPeriod.getStartDate();
            //Get ID for oldest unclosed period
            int oldestUnclosedPeriodId = oldestUnclosedPeriod.getPeriodId();
            System.out.println("Startdate of oldest unclosed period is: " + oldestUnclosedPerStartDate);
            //Get beginning balance for oldest unclosed period
            BigDecimal oldestUnclosedPerBegBal = getBeginningBalance(oldestUnclosedPeriodId);
            System.out.println("Beginning balance of oldest unclosed period is: " + oldestUnclosedPerStartDate);
            //Get expense items from start of oldest unclosed period up to day prior to desired period
            List<BalanceSheetModel> expItemsPriorToDesiredPeriodList =
                    acctBalDao.getExpItemByDatesNAcctType(acctId, oldestUnclosedPerStartDate, desiredPeriodStartDate);
            //Get desired periods starting balance
            beginningBalance = getEndingBalance(oldestUnclosedPerBegBal, expItemsPriorToDesiredPeriodList);
            System.out.println("Desired period beginning Balance: " + beginningBalance);
            //Get Balance Sheet
            List<BalanceSheetModel> response = generateBalanceSheet(beginningBalance, desiredPeriodData);
            return response;
        }
    }

    //Get beginning balance of a period
    private BigDecimal getBeginningBalance (int periodId) {

        try {
            //Check desired period for beginning balance
            List<AccountPeriodModel> begginingBalanceList = acctPeriodDao.getAcctPeriodById(periodId);

            BigDecimal beginningBalance = begginingBalanceList.get(0).getBeginningBalance();

            System.out.println("Beginning Balance : " + beginningBalance);

            return beginningBalance;
        }
        catch (EmptyResultDataAccessException e) {
            System.out.println("Period has no Beginning Balance");
            return NO_BEGINNING_BALANCE;
        }
    }

    //Get data of oldest unclosed period
    private OldestUnclosedPeriodModel getOldestUnclosedPeriod(Timestamp desiredPeriodStartDate) {
//        int lastUnclosedPeriod = 0;
        OldestUnclosedPeriodModel oldestUnclosedPeriodList = new OldestUnclosedPeriodModel();
        try {
            oldestUnclosedPeriodList = acctBalDao.getLastUnclosedPeriod(desiredPeriodStartDate);

            int oldestUnclosedPeriod = oldestUnclosedPeriodList.getPeriodId();
            System.out.println("Last unclosed period id: " + oldestUnclosedPeriod);

            System.out.println("Last unclosed period start date: " + oldestUnclosedPeriodList.getStartDate());
            return oldestUnclosedPeriodList;
        } catch (EmptyResultDataAccessException e) {
            System.out.println("No data returned for last unclosed period");
        }
        return oldestUnclosedPeriodList;
    }

    //Calculate balance sheet based on expense Items
    private BigDecimal getEndingBalance(BigDecimal startingBalance, List<BalanceSheetModel> expenseItemList){
        for( BalanceSheetModel exp : expenseItemList) {
            System.out.println("balance: " +  startingBalance);
            System.out.println("exp amount: " + exp.getAmount());

            startingBalance = startingBalance.subtract(exp.getAmount());
            System.out.println("New Bal: " + startingBalance);
        }
        return startingBalance;
    }

    //Calculate balance sheet based on expense Items
    private List<BalanceSheetModel> generateBalanceSheet(BigDecimal startingBalance, List<BalanceSheetModel> expenseItemList){
        List<BalanceSheetModel>  result = new ArrayList<BalanceSheetModel>();;
        for( BalanceSheetModel exp : expenseItemList) {

            System.out.println("balance: " +  startingBalance);
            exp.setPreBalance(startingBalance);
            System.out.println("exp amount: " + exp.getAmount());

            startingBalance = startingBalance.subtract(exp.getAmount());
            System.out.println("New Bal: " + startingBalance);
            exp.setPostBalance(startingBalance);

            result.add(exp);
        }
        return result;
    }
}
