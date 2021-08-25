package com.Allen.SpringFinancesServer.AccountBalanceSheet;

import com.Allen.SpringFinancesServer.Security.AuthorizationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RestController
public class AccountBalanceSheetController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountBalanceSheetDao dao;

    @Autowired
    AccountBalanceSheetLogic mgr;


    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAcctBalSheet")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountBalanceSheetModel> getBalanceSheetByPeriodNAcctType(
            @RequestHeader("Authorization") String jwtString,
            @QueryParam("acctId") int acctId, @QueryParam("periodId") int periodId){

        //Only assigned user may get balance sheet
        int userIdFromToken = authorizationFilter.getUserIdFromToken(jwtString);

        List<AccountBalanceSheetModel> result;
        result = mgr.balanceManager(acctId, periodId, userIdFromToken );

        return result;
    }



}
