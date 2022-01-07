package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

import com.Allen.SpringFinancesServer.Model.ApiError;
import com.Allen.SpringFinancesServer.Model.BudgIncCatRespWithName;
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
public class BudgetIncomeCategoryController {

    private static final String CLASS_NAME = "BudgetIncomeCategoryController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    BudgetIncomeCategoryLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllBudgetIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllBudgetIncomeCats(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllBudgetIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetIncomeCategoryModel> result;
        result = mgr.getAllBudgetIncomeCats(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getAllBudgetIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllBudgetIncomeCats(@RequestHeader("Authorization") String jwtString){

        final String methodName = "adminGetAllBudgetIncomeCats() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getAllBudgetIncomeCats"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<BudgetIncomeCategoryModel> result;
            result = mgr.adminGetAllBudgetIncomeCats();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getBudgetIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getBudgetIncomeCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int budIncCatid){

        final String methodName = "getBudgetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetIncomeCategoryModel> result;
        result = mgr.getBudgetIncomeCatById(budIncCatid, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    //Admin only
    @GetMapping("/Admin/getBudgetIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetBudgetIncomeCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        final String methodName = "adminGetBudgetIncomeCatById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth: Only admin may access any budget income category
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getBudgetIncomeCatById"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<BudgetIncomeCategoryModel> result;
            result = mgr.adminGetBudgetIncomeCatById(id);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Returns all budget income categories from the period the date falls within
    @GetMapping("/getBudgetIncCatsWithNameByDate")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getBudgetIncCatsWithNameByDate(
            @RequestHeader("Authorization") String jwtString, @QueryParam("date") String date){

        final String methodName = "getBudgetIncCatsWithNameByDate() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgIncCatRespWithName> result;
        result = mgr.getBudgetIncCatsWithNameByDate(date, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    @PostMapping("/addBudgetIncomeCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addBudgetIncomeCatReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody BudgetIncomeCategoryModel budgetIncomeCat) throws ServletException, IOException {

        final String methodName = "addBudgetIncomeCatReturnId() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        ///Check user auth: Only admin or assigned user may post
        int requestUserId = budgetIncomeCat.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/addBudgetIncomeCatReturnId"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<ReturnIdModel> returnedId = mgr.addBudgetIncomeCatReturnId(budgetIncomeCat);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
