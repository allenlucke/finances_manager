package com.Allen.SpringFinancesServer.IncomeItem;

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
public class IncomeItemController {

    private static final String CLASS_NAME = "IncomeItemController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    IncomeItemLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;


    @GetMapping("/getAllIncomeItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllIncomeItems(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllIncomeItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeItemModel> result;
        result = mgr.getAllIncomeItems(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getAllIncomeItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetIncomeItemById(
            @RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetIncomeItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getAllIncomeItems"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<IncomeItemModel> result;
            result = mgr.adminGetAllIncomeItems();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Admin only
    @GetMapping("/Admin/getIncomeItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetIncomeItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        final String methodName = "adminGetIncomeItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any income item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getIncomeItemById"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<IncomeItemModel> result;
            result = mgr.adminGetIncomeItemById(itemId);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getIncomeItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getIncomeItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        final String methodName = "getIncomeItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeItemModel> result;
        result = mgr.getIncomeItemById(itemId, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @PostMapping("/addIncomeItemReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addIncomeItemReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody IncomeItemModel incItem) throws ServletException, IOException {

        final String methodName = "addIncomeItemReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = incItem.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/addIncomeItemReturnId"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to logic class
        else {
            List<ReturnIdModel> returnedId = mgr.addIncomeItemReturnId(incItem);

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

    @DeleteMapping("/deleteIncomeItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity deleteIncomeItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId) {

        final String methodName = "deleteIncomeItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a delete request is made for data associated with a user other than
        //the user making the call, the dao will not delete the item
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        boolean wasDeleted = mgr.deleteIncomeItemById(itemId, userId);
        if(!wasDeleted) {
            ApiError apiError = new ApiError(
                    404,
                    HttpStatus.NOT_FOUND,
                    "Unable to delete the income item. Possible causes include: " +
                            "An incorrect income item was passed to the server.",
                    "/deleteIncomeItemById"
            );
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(apiError ,HttpStatus.NOT_FOUND);
        }
        else {
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity<>(HttpStatus.OK);
        }
    }
}
