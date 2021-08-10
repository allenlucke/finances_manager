package com.Allen.SpringFinancesServer.AccountBalance;

import com.Allen.SpringFinancesServer.Period.PeriodDao;
import com.Allen.SpringFinancesServer.Period.PeriodModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class AccountBalanceLogic {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountBalanceDao acctBalDao;

    @Autowired
    PeriodDao periodDao;

    public void balanceManager(final int acctId, final int periodId){


        //Get expense data for desired period
        List<ExpItemModel>  desiredPeriodData = acctBalDao.getExpItemByPeriodNAcctType(acctId, periodId);

        for( ExpItemModel exp : desiredPeriodData) {
            System.out.println("exp amount: " + exp.getAmount());
        }

        //Get startDate of desiredPeriod
        List<PeriodModel> desiredPeriod = periodDao.getPeriodById(periodId);
        final Timestamp desiredPeriodStartDate = desiredPeriod.get(0).getStartDate();

        System.out.println("Desired period startDate: " + desiredPeriodStartDate);

        //Get data of oldest unclosed period
        int lastUnclosedPeriod = 0;
        try {
            OldestUnclosedPeriodModel oldestUnclosedPeriodModel = acctBalDao.getLastUnclosedPeriod(desiredPeriodStartDate);

            lastUnclosedPeriod = oldestUnclosedPeriodModel.getPeriodId();

            System.out.println("Last unclosed period start date: " + oldestUnclosedPeriodModel.getStartDate());
        } catch (EmptyResultDataAccessException e) {
            System.out.println("No data returned for last unclosed period");
        }

        //Get the expense data prior to the beginning of desired period
        final int noPriorPeriod = 0;
        if(lastUnclosedPeriod != noPriorPeriod ){
            List<ExpItemModel>  priorPeriodData = acctBalDao.getExpItemByPeriodNAcctType(lastUnclosedPeriod, periodId);

            for( ExpItemModel exp : priorPeriodData) {
                System.out.println("prior period exp amount: " + exp.getAmount());
            }
        }
        else{
            System.out.println("No prior unclosed period" );
        }
        List<ExpItemModel>  priorPeriodData = acctBalDao.getExpItemByPeriodNAcctType(acctId, periodId);

        for( ExpItemModel exp : priorPeriodData) {
            System.out.println("prior period exp amount: " + exp.getAmount());
        }

        //Get desired period beginning balance

    }
}
