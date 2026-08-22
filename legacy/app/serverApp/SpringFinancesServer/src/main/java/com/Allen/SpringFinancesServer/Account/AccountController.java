package com.Allen.SpringFinancesServer.Account;

import com.Allen.SpringFinancesServer.Model.ApiError;
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
import java.math.BigDecimal;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@RestController
public class AccountController {

    private static final String CLASS_NAME = "AccountController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllAccounts")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllAccounts(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllAccounts() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<AccountModel> result;
        result = mgr.getAllAccounts(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getAllAccounts")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllAccounts(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllAccounts() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getAllAccounts"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to logic class
        else {
            List<AccountModel> result;
            result = mgr.adminGetAllAccounts();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getAccountById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAccountById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int acctId){

        final String methodName = "getAccountById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<AccountModel> result;
        result = mgr.getAccountById(acctId, userId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getAccountById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAccountById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int acctId){

        final String methodName = "adminGetAccountById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any account
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getAccountById"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to logic class
        else {
            List<AccountModel> result;
            result = mgr.adminGetAccountById(acctId);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addAccountReturningId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addAccountReturningId(
            @RequestHeader("Authorization") String jwtString, @RequestBody AccountModel account,
            @QueryParam("beginningBalance") BigDecimal beginningBalance) throws ServletException, IOException {

        final String methodName = "addAccountReturningId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = account.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/addAccountReturningId"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to logic class
        else {
            boolean hasApplicablePeriod = mgr.hasApplicablePeriod(account);
            //Is there a period that matches the account's creation date?
            //If not return error
            if(!hasApplicablePeriod){
                ApiError apiError = new ApiError(
                        416,
                        HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                        "Bad Request: The account's creation date does not fall within any previously existing period, the period must exist first.",
                        "/addAccountReturningId"
                );
                return new ResponseEntity(apiError, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
            //If so, proceed to post
            List<ReturnIdModel> returnedId = mgr.addAccountReturningId(account, beginningBalance);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
