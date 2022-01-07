package com.Allen.SpringFinancesServer.IncomeCategory;

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
public class IncomeCategoryController {

    private static final String CLASS_NAME = "IncomeCategoryController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    IncomeCategoryLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllIncomeCats(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeCategoryModel> result;
        result = mgr.getAllIncomeCats(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getAllIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllIncomeCats(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getAllIncomeCats"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<IncomeCategoryModel> result;
            result = mgr.adminGetAllIncomeCats();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Admin only
    @GetMapping("/Admin/getIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetIncomeCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int categoryId){

        final String methodName = "adminGetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any income category
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getIncomeCatById"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<IncomeCategoryModel> result;
            result = mgr.adminGetIncomeCatById(categoryId);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getIncomeCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int categoryId){

        final String methodName = "getIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeCategoryModel> result;
        result = mgr.getIncomeCatById(categoryId, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @PostMapping("/addIncomeCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addExpCatRetId(
            @RequestHeader("Authorization") String jwtString, @RequestBody IncomeCategoryModel incomeCat ) throws ServletException, IOException {

        final String methodName = "addIncomeItemReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = incomeCat.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/addIncomeCatReturnId"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ReturnIdModel> returnedId = mgr.addIncomeCatReturnId(incomeCat);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

    @DeleteMapping("/deleteIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity deleteExpItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int catId) {

        final String methodName = "deleteIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a delete request is made for data associated with a user other than
        //the user making the call, the dao will not delete the item
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        boolean wasDeleted = mgr.deleteIncomeCatById(catId, userId);
        if(!wasDeleted) {
            ApiError apiError = new ApiError(
                    404,
                    HttpStatus.NOT_FOUND,
                    "Unable to delete the income category. Possible causes include: " +
                            "The income category is currently being used as part of a budget or income item; " +
                            "OR An incorrect income category was passed to the server.",
                    "/deleteIncomeCatById"
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
