package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

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
public class BudgetExpenseCategoryController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetExpenseCategoryDao dao;

    @GetMapping("/getAllBudgetExpCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetExpenseCategoryModel> getAllBudgetExpCats(){
        List<BudgetExpenseCategoryModel> result;
        result = dao.getAllBudgetExpCats();

        return result;
    }

    @GetMapping("/getBudgetExpCat")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetExpenseCategoryModel> getBudgetExpCatById(@QueryParam("id") int id){
        List<BudgetExpenseCategoryModel> result;

        result = dao.getBudgetExpCatById(id);

        return result;
    }

    @PostMapping("/addBudgetExpCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addBudgetExpCatReturnId(@RequestBody BudgetExpenseCategoryModel budgetExpCat) {

        List<ReturnIdModel> returnedId = dao.addBudgetExpCatReturnId(budgetExpCat);
        return returnedId;
    }
}
