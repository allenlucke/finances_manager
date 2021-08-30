package com.Allen.SpringFinancesServer.IncomeItem;
;
import com.Allen.SpringFinancesServer.Period.PeriodModel;
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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    IncomeItemDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;


    @GetMapping("/getAllIncomeItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeItemModel> getAllIncomeItems(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllIncomeItems() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeItemModel> result;
        result = dao.getAllIncomeItems(userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
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
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {

            List<IncomeItemModel> result;
            result = dao.adminGetAllIncomeItems();
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
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<IncomeItemModel> result;
            result = dao.adminGetIncomeItemById(itemId);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getIncomeItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeItemModel> getIncomeItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        final String methodName = "getIncomeItemById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeItemModel> result;
        result = dao.getIncomeItemById(itemId, userId);
        LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
        return result;
    }

    @PostMapping("/addIncomeItemReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addIncomeItemReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody IncomeItemModel expItem) throws ServletException, IOException {

        final String methodName = "addIncomeItemReturnId() ";
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
            List<ReturnIdModel> returnedId = dao.addIncomeItemReturnId(expItem);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
