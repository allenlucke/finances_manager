package com.Allen.SpringFinancesServer.BudgetIncomeCategory;

import com.Allen.SpringFinancesServer.BudgetExpenseCategory.BudgetExpenseCategoryModel;
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
public class BudgetIncomeCategoryController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    BudgetIncomeCategoryDao dao;

    @GetMapping("/getAllBudgetIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetIncomeCategoryModel> getAllBudgetIncomeCats(){
        List<BudgetIncomeCategoryModel> result;
        result = dao.getAllBudgetIncomeCats();

        return result;
    }

    @GetMapping("/getBudgetIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<BudgetIncomeCategoryModel> getBudgetIncomeCatById(@QueryParam("id") int id){
        List<BudgetIncomeCategoryModel> result;

        result = dao.getBudgetIncomeCatById(id);

        return result;
    }

    @PostMapping("/addBudgetIncomeCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addBudgetIncomeCatReturnId(@RequestBody BudgetIncomeCategoryModel budgetIncomeCat) {

        List<ReturnIdModel> returnedId = dao.addBudgetIncomeCatReturnId(budgetIncomeCat);
        return returnedId;
    }
}
