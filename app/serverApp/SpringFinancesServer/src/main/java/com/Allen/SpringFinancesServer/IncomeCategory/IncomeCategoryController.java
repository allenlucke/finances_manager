package com.Allen.SpringFinancesServer.IncomeCategory;

import com.Allen.SpringFinancesServer.IncomeItem.IncomeItemModel;
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
public class IncomeCategoryController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    IncomeCategoryDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    @GetMapping("/getAllIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeCategoryModel> getAllIncomeCats(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeCategoryModel> result;
        result = dao.getAllIncomeCats(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllIncomeCats(@RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<IncomeCategoryModel> result;
            result = dao.adminGetAllIncomeCats();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Admin only
    @GetMapping("/Admin/getIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetIncomeCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int categoryId){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<IncomeCategoryModel> result;

            result = dao.adminGetIncomeCatById(categoryId);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeCategoryModel> getIncomeCatById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int categoryId){

        //May only get categories assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeCategoryModel> result;

        result = dao.getIncomeCatById(categoryId, userId);

        return result;
    }

    @PostMapping("/addIncomeCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addExpCatRetId(
            @RequestHeader("Authorization") String jwtString, @RequestBody IncomeCategoryModel incomeCat ) throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = incomeCat.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ReturnIdModel> returnedId = dao.addIncomeCatReturnId(incomeCat);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
