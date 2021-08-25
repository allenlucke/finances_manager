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

@RestController
public class ExpenseItemController {

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

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseItemModel> result;
        result = dao.getAllExpItems(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllExpItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllExpItems(@RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ExpenseItemModel> result;
            result = dao.adminGetAllExpItems();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getExpItem")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseItemModel> getExpItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        //May only get categories assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseItemModel> result;

        result = dao.getExpItemById(itemId, userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getExpItem")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetExpItemById(@RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<ExpenseItemModel> result;

            result = dao.adminGetExpItemById(itemId);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addExpItemRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addExpItemReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody ExpenseItemModel expItem) throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = expItem.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            //Check to see if account type is credit
            expItem = logic.setPaidWithCredit(expItem);
            List<ReturnIdModel> returnedId = dao.addExpItemReturnId(expItem);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }
}
