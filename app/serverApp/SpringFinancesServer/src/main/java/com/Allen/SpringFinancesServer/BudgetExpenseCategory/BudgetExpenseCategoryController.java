package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

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
public class BudgetExpenseCategoryController {

    private static final String CLASS_NAME = "BudgetExpenseCategoryController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetExpenseCategoryDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllBudgetExpCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetExpenseCategoryModel> getAllBudgetExpCats(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllBudgetExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetExpenseCategoryModel> result;
        result = dao.getAllBudgetExpCats(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllBudgetExpCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllBudgetExpCats(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllBudgetExpCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<BudgetExpenseCategoryModel> result;
            result = dao.adminGetAllBudgetExpCats();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Returns all budget expense categories from the period the date falls within
    @GetMapping("/getBudgetExpCatsBtDate")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetExpenseCategoryModel> getBudgetExpCatsBtDate(
            @RequestHeader("Authorization") String jwtString, @QueryParam("date") String date){

        final String methodName = "getBudgetExpCatsBtDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetExpenseCategoryModel> result;
        result = dao.getBudgetExpCatsBtDate(date, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    @GetMapping("/getBudgetExpCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetExpenseCategoryModel> getBudgetExpCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int budgExpCatId){

        final String methodName = "getBudgetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetExpenseCategoryModel> result;
        result = dao.getBudgetExpCatById(budgExpCatId, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only
    @GetMapping("/Admin/getBudgetExpCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetBudgetExpCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        final String methodName = "adminGetBudgetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any budget expense category
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<BudgetExpenseCategoryModel> result;
            result = dao.adminGetBudgetExpCatById(id);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addBudgetExpCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addBudgetExpCatReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody BudgetExpenseCategoryModel budgetExpCat) throws ServletException, IOException {

        final String methodName = "addBudgetExpCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = budgetExpCat.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ReturnIdModel> returnedId = dao.addBudgetExpCatReturnId(budgetExpCat);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
