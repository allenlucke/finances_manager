package com.Allen.SpringFinancesServer.User;

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

@RestController
public class UserController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    UserDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;


    @GetMapping("/getUserFirstName")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ResponseEntity getUserFirstName(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id) throws ServletException, IOException {

        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, id);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            String respString;
            respString = dao.getUserFirstName(id);
            return new ResponseEntity(respString, HttpStatus.OK);
        }
    }

    @GetMapping("/getUser")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getUserById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id) throws ServletException, IOException {

        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, id);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<UserModel> result;

            result = dao.getUserById(id);

            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Admin only service
    @GetMapping("/Admin/getAllUsers")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getAllUsers(@RequestHeader("Authorization") String jwtString){

        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<UserModel> result;
            result = dao.getAllUsers();

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
}
