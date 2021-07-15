package com.Allen.SpringFinancesServer.BudgetExpenseCategory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RestController
public class BudgetExpenseCategoryService {

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
}
