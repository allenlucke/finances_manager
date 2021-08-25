package com.Allen.SpringFinancesServer.BudgetBalanceSheet;

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
public class BudgetBalanceController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetBalanceSheetDao dao;

    @Autowired
    BudgetBalanceSheetLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getBudgetBalanceSheetByPeriod")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetBalanceSheetModel> getBudgetBalanceSheetByPeriod(
            @RequestHeader("Authorization") String jwtString, @QueryParam("periodId") int periodId){

        //Only assigned user may get balance sheet
        int userIdFromToken = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetBalanceSheetModel> result;
        result = mgr.balanceSheetByPeriodManager( periodId, userIdFromToken );

        return result;
    }

}
