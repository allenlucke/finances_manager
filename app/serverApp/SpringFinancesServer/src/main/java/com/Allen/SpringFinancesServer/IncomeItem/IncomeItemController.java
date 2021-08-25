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

@RestController
public class IncomeItemController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    IncomeItemDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;


    @GetMapping("/getAllIncomeItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeItemModel> getAllIncomeItems(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeItemModel> result;
        result = dao.getAllIncomeItems(userId);

        return result;
    }

    //Admin only
    @GetMapping("/Admin/getAllIncomeItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetIncomeItemById(
            @RequestHeader("Authorization") String jwtString){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<IncomeItemModel> result;
            result = dao.adminGetAllIncomeItems();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    //Admin only
    @GetMapping("/Admin/getIncomeItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetIncomeItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        //Only Admin may get item
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<IncomeItemModel> result;

            result = dao.adminGetIncomeItemById(itemId);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getIncomeItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeItemModel> getIncomeItemById(
            @RequestHeader("Authorization") String jwtString, @QueryParam("id") int itemId){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<IncomeItemModel> result;

        result = dao.getIncomeItemById(itemId, userId);

        return result;
    }

    @PostMapping("/addIncomeItemReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addIncomeItemReturnId(
            @RequestHeader("Authorization") String jwtString, @RequestBody IncomeItemModel expItem) throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = expItem.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {
            List<ReturnIdModel> returnedId = dao.addIncomeItemReturnId(expItem);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }

}
