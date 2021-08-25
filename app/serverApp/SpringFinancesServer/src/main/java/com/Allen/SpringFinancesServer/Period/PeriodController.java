package com.Allen.SpringFinancesServer.Period;

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
public class PeriodController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    PeriodDao dao;

    @Autowired
    AuthorizationFilter authorizationFilter;

    //Admin Only
    @GetMapping("/Admin/getAllPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetAllPeriods(@RequestHeader("Authorization") String jwtString){

        //Only admin may getAllPeriods
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<PeriodModel> result;
            result = dao.adminGetAllPeriods();
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getAllPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<PeriodModel> getAllPeriods(@RequestHeader("Authorization") String jwtString){

        //May only get periods assigned to the user
        int userId = authorizationFilter.getUserIdFromToken(jwtString);

        List<PeriodModel> result;
        result = dao.getAllPeriods(userId);

        return result;
    }

    //Admin Only
    @GetMapping("/Admin/getPeriod")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity adminGetPeriodById(@RequestHeader("Authorization") String jwtString, @QueryParam("id") int id){

        //Only Admin may get period
        boolean confirmAuthorization = authorizationFilter.doFilterBySecurityLevel(jwtString);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<PeriodModel> result;
            result = dao.adminGetPeriodById(id);
            return new ResponseEntity(result, HttpStatus.OK);
        }
    }

    @GetMapping("/getPeriod")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity getPeriodById(@RequestHeader("Authorization") String jwtString, @QueryParam("id") int periodId){

        //Only assigned user may get period
        int userIdFromToken = authorizationFilter.getUserIdFromToken(jwtString);

            List<PeriodModel> result;
            result = dao.getPeriodById(periodId, userIdFromToken);
            return new ResponseEntity(result, HttpStatus.OK);

    }

    @PostMapping("/addPeriodRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public ResponseEntity addPeriodRetId(
            @RequestHeader("Authorization") String jwtString, @RequestBody PeriodModel period) throws ServletException, IOException {

        //Only admin or assigned user may post
        int requestUserId = period.getUsersId();
        boolean confirmAuthorization = authorizationFilter.doFilterByUserIdOrSecurityLevel(jwtString, requestUserId);

        if(!confirmAuthorization) {
            System.out.println("Auth Status: " + confirmAuthorization);

            return new ResponseEntity("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
        else {

            List<ReturnIdModel> returnedId = dao.addPeriodReturnId(period);
            return new ResponseEntity(returnedId, HttpStatus.OK);
        }
    }
}
