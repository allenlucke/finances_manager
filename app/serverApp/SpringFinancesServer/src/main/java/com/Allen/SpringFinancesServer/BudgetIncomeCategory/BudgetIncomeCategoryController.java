package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

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
public class BudgetIncomeCategoryController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetIncomeCategoryDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllBudgetIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetIncomeCategoryModel> getAllBudgetIncomeCats(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<BudgetIncomeCategoryModel> result;
        result = dao.getAllBudgetIncomeCats(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllBudgetIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllBudgetIncomeCats(@RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<BudgetIncomeCategoryModel> result;
            result = dao.adminGetAllBudgetIncomeCats();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getBudgetIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetIncomeCategoryModel> getBudgetIncomeCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int budIncCatid){

        //May only get categories assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);
        List<BudgetIncomeCategoryModel> result;

        result = dao.getBudgetIncomeCatById(budIncCatid, userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getBudgetIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetBudgetIncomeCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {


            List<BudgetIncomeCategoryModel> result;

            result = dao.adminGetBudgetIncomeCatById(id);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addBudgetIncomeCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addBudgetIncomeCatReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody BudgetIncomeCategoryModel budgetIncomeCat) throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = budgetIncomeCat.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ReturnIdModel> returnedId = dao.addBudgetIncomeCatReturnId(budgetIncomeCat);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
