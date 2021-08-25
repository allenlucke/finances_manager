package com.Allen.SpringFinancesServer.ExpenseCategory;

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
public class ExpenseCategoryController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    ExpenseCategoryDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllExpCat")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseCategoryModel> getAllExpCat(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseCategoryModel> result;
        result = dao.getAllExpCats(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllExpCat")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllExpCat(@RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ExpenseCategoryModel> result;
            result = dao.adminGetAllExpCats();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getExpCatByIdById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseCategoryModel> getExpCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int categoryId){
        //May only get categories assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<ExpenseCategoryModel> result;

        result = dao.getExpCatById(categoryId, userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getExpCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetExpCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<ExpenseCategoryModel> result;

            result = dao.adminGetExpCatById(id);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @PostMapping("/addExpCatRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addExpCatRetId(
            @RequestHeader("Authorization") String jwtString, @RequestBody ExpenseCategoryModel expCat ) throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = expCat.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ReturnIdModel> returnedId = dao.addExpCatReturnId(expCat);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }
}
