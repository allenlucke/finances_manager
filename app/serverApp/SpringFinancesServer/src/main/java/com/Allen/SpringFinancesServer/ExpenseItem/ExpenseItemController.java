package com.Allen.SpringFinancesServer.ExpenseItem;

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
    ExpenseItemLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllExpItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllExpItems(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllExpItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseItemModel> result;
        result = mgr.getAllExpItems(userId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
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
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getAllExpItems"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ExpenseItemModel> result;
            result = mgr.adminGetAllExpItems();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getExpItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getExpItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        final String methodName = "getExpItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseItemModel> result;
        result = mgr.getExpItemById(itemId, userId);

        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
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
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getExpItemById"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ExpenseItemModel> result;
            result = mgr.adminGetExpItemById(itemId);
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
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/addExpItemRetId"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to logic class
        else {
            List<ReturnIdModel> returnedId = mgr.addExpItemReturnId(expItem);

            //Check if post was rejected by logic class
            if(returnedId.size() > 0) {
                //Post NOT rejected
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                return new ResponseEntity(returnedId, HttpStatus.OK);
            }
            else{
                //Post rejected
                ApiError apiError = new ApiError(
                        416,
                        HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                        "Unable to post expense item, possible causes include: " +
                                "Desired account is not active as of the expense item's received date. " +
                                " Desired transaction date has no matching budget expense item categories.",
                        "/addExpItemRetId"
                );
                LOGGER.warn(CLASS_NAME + methodName + " Input data invalid.");
                LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
                return new ResponseEntity(apiError, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
        }
    }

    @DeleteMapping("/deleteExpItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity deleteExpItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId) {

        final String methodName = "deleteExpItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a delete request is made for data associated with a user other than
        //the user making the call, the dao will not delete the item
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        boolean wasDeleted = mgr.deleteExpItemById(itemId, userId);
        if(!wasDeleted) {
            ApiError apiError = new ApiError(
                    404,
                    HttpStatus.NOT_FOUND,
                    "Unable to delete the expense item. Possible causes include: " +
                            "An incorrect expense item was passed to the server.",
                    "/deleteExpItemById"
            );
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(apiError, HttpStatus.NOT_FOUND);
        }
        else {
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity<>(HttpStatus.OK);
        }
    }

}
