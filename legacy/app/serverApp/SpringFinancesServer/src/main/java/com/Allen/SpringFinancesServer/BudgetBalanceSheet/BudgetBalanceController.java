package com.Allen.SpringFinancesServer.BudgetBalanceSheet;

import com.Allen.SpringFinancesServer.Security.AuthorizationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@RestController
public class BudgetBalanceController {

    private static final String CLASS_NAME = "BudgetBalanceController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetBalanceSheetLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getBudgetBalanceSheetByPeriod")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getBudgetBalanceSheetByPeriod(
            @RequestHeader("Authorization") String jwtString, @QueryParam("periodId") int periodId){

        final String methodName = "getBudgetBalanceSheetByPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userIdFromToken = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetBalanceSheetModel> result;
        result = mgr.balanceSheetByPeriodManager( periodId, userIdFromToken );

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

}
