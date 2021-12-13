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

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@RestController
public class AccountPeriodController {

    private static final String CLASS_NAME = "AccountPeriodController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    AccountPeriodLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllAcctPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllAccountPeriods(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllAccountPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<AccountPeriodModel> result;
        result = mgr.getAllAccountPeriods(userId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getAllAcctPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllAccountPeriods(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllAccountPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<AccountPeriodModel> result;
            result = mgr.adminGetAllAccountPeriods();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getAcctPeriodById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAcctPeriodById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int acctPeriodId){

        final String methodName = "getAcctPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<AccountPeriodModel> result;
        result = mgr.getAcctPeriodById(acctPeriodId, userId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getAcctPeriodById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAcctPeriodById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int acctPeriodId){

        final String methodName = "adminGetAcctPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any account period
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<AccountPeriodModel> result;
            result = mgr.adminGetAcctPeriodById(acctPeriodId);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addAccountPeriodRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addAcctPeriodReturningId(
            @RequestHeader("Authorization") String jwtString, @RequestBody AccountPeriodModel acctPeriod)
            throws ServletException, IOException {

        final String methodName = "addAcctPeriodReturningId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = acctPeriod.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to logic class
        else {
            List<ReturnIdModel> returnedId = mgr.addAcctPeriodReturningId(acctPeriod);

            //Check to see if post was rejected due to an overlapping accountPeriod
            if(returnedId.size() == 0){
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                LOGGER.warn(CLASS_NAME + methodName + "This request overlaps/conflicts with a previously existing accountPeriod.");
                return new ResponseEntity("Bad Request", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
            //Else return response
            else {
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                return new ResponseEntity(returnedId, HttpStatus.OK);
            }
        }
    }
}
