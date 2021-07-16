package com.Allen.SpringFinancesServer.Budget;

import com.Allen.SpringFinancesServer.ReturnIdModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RestController
public class BudgetController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetDao dao;

    @GetMapping("/getAllBudgets")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetModel> getAllBudgets(){
        List<BudgetModel> result;
        result = dao.getAllBudgets();

        return result;
    }

    @GetMapping("/getBudget")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetModel> getBudgetById(@QueryParam("id") int id){
        List<BudgetModel> result;

        result = dao.getBudgetById(id);

        return result;
    }

    @PostMapping("/addBudgetRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addBudgetRetId(@RequestBody BudgetModel budget) {
        List<ReturnIdModel> returnedId = dao.addBudgetReturnId(budget);
        return returnedId;
    }
}
