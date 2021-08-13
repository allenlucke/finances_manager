package com.Allen.SpringFinancesServer.IncomeCategory;

import com.Allen.SpringFinancesServer.ExpenseCategory.ExpenseCategoryModel;
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
public class IncomeCategoryController {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    IncomeCategoryDao dao;

    @GetMapping("/getAllIncomeCats")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeCategoryModel> getAllIncomeCats(){
        List<IncomeCategoryModel> result;
        result = dao.getAllIncomeCats();

        return result;
    }

    @GetMapping("/getIncomeCatById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeCategoryModel> getIncomeCatById(@QueryParam("id") int id){
        List<IncomeCategoryModel> result;

        result = dao.getIncomeCatById(id);

        return result;
    }

    @PostMapping("/addIncomeCatReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addExpCatRetId(@RequestBody IncomeCategoryModel incomeCat ) {
        List<ReturnIdModel> returnedId = dao.addIncomeCatReturnId(incomeCat);
        return returnedId;
    }
}
