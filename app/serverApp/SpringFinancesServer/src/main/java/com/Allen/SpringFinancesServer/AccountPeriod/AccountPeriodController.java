package com.Allen.SpringFinancesServer.AccountPeriod;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import com.Allen.SpringFinancesServer.Security.AuthorizationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;

@RestController
public class AccountPeriodController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountPeriodDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllAcctPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountPeriodModel> getAllAccountPeriods(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);
        List<AccountPeriodModel> result;
        result = dao.getAllAccountPeriods(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllAcctPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllAccountPeriods(@RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<AccountPeriodModel> result;
            result = dao.adminGetAllAccountPeriods();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getAcctPeriodById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountPeriodModel> getAcctPeriodById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int acctPeriodId){

        //May only get categories assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);
        List<AccountPeriodModel> result;

        result = dao.getAcctPeriodById(acctPeriodId, userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAcctPeriodById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAcctPeriodById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int acctPeriodId){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<AccountPeriodModel> result;

            result = dao.adminGetAcctPeriodById(acctPeriodId);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addAccountPeriodRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addAcctPeriodReturningId(
            @RequestHeader("Authorization") String jwtString, @RequestBody AccountPeriodModel acctPeriod)
            throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = acctPeriod.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ReturnIdModel> returnedId = dao.addAcctPeriodReturningId(acctPeriod);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }
}
