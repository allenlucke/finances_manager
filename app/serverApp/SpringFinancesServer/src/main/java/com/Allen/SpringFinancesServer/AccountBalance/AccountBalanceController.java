package com.Allen.SpringFinancesServer.AccountBalance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RestController
public class AccountBalanceController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountBalanceDao dao;

    @Autowired
    AccountBalanceLogic mgr;

    @GetMapping("/getAcctBalTest")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BalanceSheetModel> getExpItemByPeriodNAcctType(@QueryParam("acctId") int acctId,
                                                          @QueryParam("periodId") int periodId){
        List<BalanceSheetModel> result;
//        result = dao.getExpItemByPeriodNAcctType( acctId, periodId );
        result = mgr.balanceManager(acctId, periodId );
//        MathContext mc = new MathContext(2);
//        BigDecimal acctBal = BigDecimal.valueOf(12000.00);
//        for( ExpItemModel exp : result) {
//
//            System.out.println("balance: " +  acctBal);
//            System.out.println("exp amount: " + exp.getAmount());
//
//            acctBal = acctBal.subtract(exp.getAmount());
//
//            System.out.println("New Bal: " + acctBal);
//
//
//        }

        return result;
    }



}