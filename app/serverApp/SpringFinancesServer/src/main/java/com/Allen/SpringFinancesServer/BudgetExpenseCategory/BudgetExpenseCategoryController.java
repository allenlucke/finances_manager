package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

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
public class BudgetExpenseCategoryController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetExpenseCategoryDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllBudgetExpCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetExpenseCategoryModel> getAllBudgetExpCats(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetExpenseCategoryModel> result;
        result = dao.getAllBudgetExpCats(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllBudgetExpCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllBudgetExpCats(@RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<BudgetExpenseCategoryModel> result;
            result = dao.adminGetAllBudgetExpCats();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getBudgetExpCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetExpenseCategoryModel> getBudgetExpCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int budgExpCatId){

        //May only get categories assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);
        List<BudgetExpenseCategoryModel> result;

        result = dao.getBudgetExpCatById(budgExpCatId, userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getBudgetExpCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetBudgetExpCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<BudgetExpenseCategoryModel> result;

            result = dao.adminGetBudgetExpCatById(id);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addBudgetExpCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addBudgetExpCatReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody BudgetExpenseCategoryModel budgetExpCat) throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = budgetExpCat.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ReturnIdModel> returnedId = dao.addBudgetExpCatReturnId(budgetExpCat);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
