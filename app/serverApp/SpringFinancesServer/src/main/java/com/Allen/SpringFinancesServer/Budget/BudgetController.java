package com.Allen.SpringFinancesServer.Budget;

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
public class BudgetController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllBudgets")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetModel> getAllBudgets(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetModel> result;
        result = dao.getAllBudgets(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllBudgets")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllBudgets(@RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<BudgetModel> result;
            result = dao.adminGetAllBudgets();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getBudgetById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetModel> getBudgetById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int budgetId){

        //May only get categories assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);
        List<BudgetModel> result;

        result = dao.getBudgetById(budgetId, userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getBudgetById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetBudgetById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<BudgetModel> result;

            result = dao.adminGetBudgetById(id);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addBudgetRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addBudgetRetId(
            @RequestHeader("Authorization") String jwtString, @RequestBody BudgetModel budget)
            throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = budget.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ReturnIdModel> returnedId = dao.addBudgetReturnId(budget);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }
}
