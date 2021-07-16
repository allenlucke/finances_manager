package com.Allen.SpringFinancesServer.ExpenseCategory;

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
public class ExpenseCategoryController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    ExpenseCategoryDao dao;

    @GetMapping("/getAllExpCat")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseCategoryModel> getAllExpCat(){
        List<ExpenseCategoryModel> result;
        result = dao.getAllExpCats();

        return result;
    }

    @GetMapping("/getExpCat")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseCategoryModel> getExpCatById(@QueryParam("id") int id){
        List<ExpenseCategoryModel> result;

        result = dao.getExpCatById(id);

        return result;
    }

    @PostMapping("/addExpCatRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addExpCatRetId(@RequestBody ExpenseCategoryModel expCat ) {
        List<ReturnIdModel> returnedId = dao.addExpCatReturnId(expCat);
        return returnedId;
    }
}
