package com.Allen.SpringFinancesServer.Period;

import com.Allen.SpringFinancesServer.Model.ApiError;
import com.Allen.SpringFinancesServer.ReturnIdModel;
import com.Allen.SpringFinancesServer.Security.AuthorizationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@RestController
public class PeriodController {

    private static final String CLASS_NAME = "PeriodController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    PeriodLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    //Admin Only
    @GetMapping("/Admin/getAllPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllPeriods(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getAllPeriods"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<PeriodModel> result;
            result = mgr.adminGetAllPeriods();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getAllPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllPeriods(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllPeriods() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<PeriodModel> result;
        result = mgr.getAllPeriods(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @GetMapping("/getCurrentPeriod")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getCurrentPeriod(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getCurrentPeriod() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userIdFromToken = authorizationFilter.getUserIdFromToken(jwtString);

        List<PeriodModel> result;
        result = mgr.getCurrentPeriod(userIdFromToken);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin Only
    @GetMapping("/Admin/getPeriodById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetPeriodById(@RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        final String methodName = "adminGetPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any periods
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getPeriodById"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<PeriodModel> result;
            result = mgr.adminGetPeriodById(id);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getPeriodById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getPeriodById(@RequestHeader("Authorization") String jwtString, @QueryParam("id") int periodId){

        final String methodName = "getPeriodById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userIdFromToken = authorizationFilter.getUserIdFromToken(jwtString);

        List<PeriodModel> result;
        result = mgr.getPeriodById(periodId, userIdFromToken);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @GetMapping("/getPeriodByDate")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getPeriodByDate(@RequestHeader("Authorization") String jwtString, @QueryParam("searchDate") String searchDate){

        final String methodName = "getPeriodByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userIdFromToken = authorizationFilter.getUserIdFromToken(jwtString);

        List<PeriodModel> result;
        result = mgr.getPeriodByDate(searchDate, userIdFromToken);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @GetMapping("/getPeriodsWithoutBudget")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity  getPeriodsWithoutBudget(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getPeriodsWithoutBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<PeriodModel> result;
        result = mgr.getPeriodsWithoutBudget(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @PostMapping("/addPeriodRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addPeriodRetId(
            @RequestHeader("Authorization") String jwtString, @RequestBody PeriodModel period) throws ServletException, IOException {

        final String methodName = "addPeriodRetId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin or assigned user may post
        int requestUserId = period.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/addPeriodRetId"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to logic class
        else {
            List<ReturnIdModel> returnedId = mgr.addPeriodRetId(period, requestUserId);

            //Check if post was rejected by logic class
            if (returnedId.size() > 0) {
                //Post NOT rejected
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                return new ResponseEntity(returnedId, HttpStatus.OK);
            } else {
                //Post rejected
                ApiError apiError = new ApiError(
                        416,
                        HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                        "Unable to post income item, possible causes include: " +
                                " Desired account is a credit account, income cannot be posted to a credit account. " +
                                "Desired account is not active as of the income item's received date. " +
                                "Desired received date has no matching budget income item categories.",
                        "/addIncomeItemReturnId"
                );
                LOGGER.warn(CLASS_NAME + methodName + " Input data invalid.");
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                return new ResponseEntity(apiError, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
        }
    }
}
