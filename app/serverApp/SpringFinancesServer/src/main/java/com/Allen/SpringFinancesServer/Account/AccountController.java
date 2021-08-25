package com.Allen.SpringFinancesServer.Account;

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
public class AccountController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllAccounts")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountModel> getAllAccounts(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);
        List<AccountModel> result;
        result = dao.getAllAccounts(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllAccounts")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllAccounts(@RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<AccountModel> result;
            result = dao.adminGetAllAccounts();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getAccountById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountModel> getAccountById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int acctId){

        //May only get categories assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);
        List<AccountModel> result;

        result = dao.getAccountById(acctId, userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAccountById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAccountById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int acctId){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<AccountModel> result;

            result = dao.adminGetAccountById(acctId);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addAccountReturningId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addAccountReturningId(
            @RequestHeader("Authorization") String jwtString, @RequestBody AccountModel acct)
            throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = acct.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ReturnIdModel> returnedId = dao.addAccountReturningId(acct);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
