package com.Allen.SpringFinancesServer.Budget;

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
public class BudgetController {

    private static final String CLASS_NAME = "BudgetController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllBudgets")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllBudgets(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllBudgets() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetModel> result;
        result = mgr.getAllBudgets(userId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getAllBudgets")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllBudgets(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllBudgets() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<BudgetModel> result;
            result = mgr.adminGetAllBudgets();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getBudgetById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getBudgetById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int budgetId){

        final String methodName = "getBudgetById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetModel> result;
        result = mgr.getBudgetById(budgetId, userId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getBudgetById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetBudgetById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        final String methodName = "adminGetBudgetById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any budget
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<BudgetModel> result;
            result = mgr.adminGetBudgetById(id);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addBudgetRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addBudgetRetId(
            @RequestHeader("Authorization") String jwtString, @RequestBody BudgetModel budget)
            throws ServletException, IOException {

        final String methodName = "addBudgetRetId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin or assigned user may post
        int requestUserId = budget.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to logic class
        else {
            List<ReturnIdModel> returnedId = mgr.addBudgetReturnId(budget);

            //Check to see if post was rejected due to a previously existing budget already assigned to the periodId
            if(returnedId.size() == 0){
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                LOGGER.warn(CLASS_NAME + methodName + "This request overlaps/conflicts with a previously existing budget.");
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
