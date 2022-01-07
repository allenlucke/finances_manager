package com.Allen.SpringFinancesServer.User;

import com.Allen.SpringFinancesServer.Model.ApiError;
import com.Allen.SpringFinancesServer.Security.AuthorizationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.List;

import static com.Allen.SpringFinancesServer.SpringFinancesServerApplication.LOGGER;

@RestController
public class UserController {

    private static final String CLASS_NAME = "UserController --- ";
    private static final String METHOD_ENTERING = "Entering:  ";
    private static final String METHOD_EXITING = "Exiting:  ";

    @Autowired
    UserLogic mgr;

    @Autowired
    AuthorizationFilter authorizationFilter;


    @GetMapping("/getUserFirstName")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseEntity getUserFirstName(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id) throws ServletException, IOException {

        final String methodName = "getUserFirstName() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, id);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/getUserFirstName"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            String respString;
            respString = mgr.getUserFirstName(id);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(respString, HttpStatus.OK);
        }
    }

    @GetMapping("/getUser")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getUserById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id) throws ServletException, IOException {

        final String methodName = "getUserById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, id);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/getUserFirstName"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<UserModel> result;
            result = mgr.getUserById(id);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getUserByName")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getUserByName(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id) throws ServletException, IOException {

        final String methodName = "getUserById() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, id);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/getUserByName"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<UserModel> result;
            result = mgr.getUserById(id);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Admin only service
    @GetMapping("/Admin/getAllUsers")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllUsers(@RequestHeader("Authorization") String jwtString){

        final String methodName = "getAllUsers() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Check user auth
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);
        if(!confirmAuthorization) {
            ApiError apiError = new ApiError(
                    401,
                    HttpStatus.UNAUTHORIZED,
                    "User is not authorized to access these records",
                    "/Admin/getAllUsers"
            );
            LOGGER.warn(CLASS_NAME + methodName + "User is not authorized to access these records");
            return new ResponseEntity(apiError, HttpStatus.UNAUTHORIZED);
        }
        //If authorized make call to dao
        else {
            List<UserModel> result;
            result = mgr.getAllUsers();
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Used for application Client for local storage user data
    @GetMapping("/getUserByToken")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getUserByToken(
            @RequestHeader("Authorization") String jwtString) throws ServletException, IOException {

        final String methodName = "getUserByToken() ";
        LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

        //Get the user id of user making the call
        //If a request is made for data associated with a user other than
        //the user making the call, the dao will return an empty result
        //set from the database
        int userIdFromToken = authorizationFilter.getUserIdFromToken(jwtString);
            List< ClientLocalStorageUser> result;
            result = mgr.getUserByToken(userIdFromToken);
            LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }


    //
    //BELOW METHODS TEMPORARILY COMMENTED OUT, need to add encryption to password, etc
    //

//    @PostMapping("/addUserRetId")
//    @Consumes(MediaType.APPLICATION_JSON)
//    public int addUser(@RequestBody UserModel usr) {
//        int returnedId = dao.addUserReturnId(usr);
//        return returnedId;
//    }
//
//    @PostMapping("/addUserRetUsr")
//    @Consumes(MediaType.APPLICATION_JSON)
//    public UserModel addUserRetUsr(@RequestBody UserModel usr) {
//        UserModel user = dao.addUserReturnUser(usr);
//        return user;
//    }

