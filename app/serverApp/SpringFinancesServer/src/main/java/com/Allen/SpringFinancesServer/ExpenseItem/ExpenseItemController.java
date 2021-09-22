package com.Allen.SpringFinancesServer.ExpenseItem;

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
public class ExpenseItemController {

    private static final String CLASS_NAME = "ExpenseItemController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    ExpenseItemDao dao;

    @Autowired
    ExpenseItemLogic logic;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllExpItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseItemModel> getAllExpItems(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllExpItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseItemModel> result;
        result = dao.getAllExpItems(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllExpItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllExpItems(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllExpItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ExpenseItemModel> result;
            result = dao.adminGetAllExpItems();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getExpItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseItemModel> getExpItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        final String methodName = "getExpItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseItemModel> result;
        result = dao.getExpItemById(itemId, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    //Admin only
    @GetMapping("/Admin/getExpItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetExpItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        final String methodName = "adminGetExpItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any expense item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ExpenseItemModel> result;
            result = dao.adminGetExpItemById(itemId);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addExpItemRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addExpItemReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody ExpenseItemModel expItem)
            throws ServletException, IOException {

        final String methodName = "addExpItemReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = expItem.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            //Check to see if account type is credit
            expItem = logic.setPaidWithCredit(expItem);
            List<ReturnIdModel> returnedId = dao.addExpItemReturnId(expItem);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

    @DeleteMapping("deleteExpItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity deleteExpItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId) {

        final String methodName = "deleteExpItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a delete request is made for data associated with a user other than
        //the user making the call, the dao will not delete the item
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        boolean wasDeleted = dao.deleteExpItemById(itemId, userId);
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
