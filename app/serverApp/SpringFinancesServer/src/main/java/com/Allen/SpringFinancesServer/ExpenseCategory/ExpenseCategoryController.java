package com.Allen.SpringFinancesServer.ExpenseCategory;

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
public class ExpenseCategoryController {

    private static final String CLASS_NAME = "ExpenseCategoryController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    ExpenseCategoryDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllExpCat")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseCategoryModel> getAllExpCat(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllExpCat() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseCategoryModel> result;
        result = dao.getAllExpCats(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllExpCat")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllExpCat(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllExpCat() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ExpenseCategoryModel> result;
            result = dao.adminGetAllExpCats();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getExpCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseCategoryModel> getExpCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int categoryId){

        final String methodName = "getExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseCategoryModel> result;
        result = dao.getExpCatById(categoryId, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only
    @GetMapping("/Admin/getExpCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetExpCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        final String methodName = "adminGetExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any expense category
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ExpenseCategoryModel> result;
            result = dao.adminGetExpCatById(id);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Get available expense categories not assigned to the input budget
    @GetMapping("/getExpenseCatsNotAssignedToBudget")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseCategoryModel> getExpenseCatsNotAssignedToBudget(
            @RequestHeader("Authorization") String jwtString, @QueryParam("budgetId") int budgetId){

        final String methodName = "getExpenseCatsNotAssignedToBudget() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseCategoryModel> result;
        result = dao.getExpenseCatsNotAssignedToBudget(budgetId, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    @PostMapping("/addExpCatRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addExpCatRetId(
            @RequestHeader("Authorization") String jwtString, @RequestBody ExpenseCategoryModel expCat )
            throws ServletException, IOException {

        final String methodName = "addExpCatRetId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = expCat.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ReturnIdModel> returnedId = dao.addExpCatReturnId(expCat);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

    @DeleteMapping("deleteExpCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity deleteExpItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int catId) {

        final String methodName = "deleteExpCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a delete request is made for data associated with a user other than
        //the user making the call, the dao will not delete the item
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        boolean wasDeleted = dao.deleteExpCatById(catId, userId);
        if(!wasDeleted) {
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        else {
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity<>(HttpStatus.OK);
        }
    }
}
