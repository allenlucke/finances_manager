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
    BudgetDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllBudgets")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetModel> getAllBudgets(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllBudgets() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetModel> result;
        result = dao.getAllBudgets(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
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
            result = dao.adminGetAllBudgets();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getBudgetById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetModel> getBudgetById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int budgetId){

        final String methodName = "getBudgetById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetModel> result;
        result = dao.getBudgetById(budgetId, userId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
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
            result = dao.adminGetBudgetById(id);
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

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = budget.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ReturnIdModel> returnedId = dao.addBudgetReturnId(budget);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }
}
