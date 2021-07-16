package com.Allen.SpringFinancesServer.ExpenseItem;

import com.Allen.SpringFinancesServer.Period.PeriodModel;
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
public class ExpenseItemController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    ExpenseItemDao dao;

    @Autowired
    ExpenseItemLogic logic;

    @GetMapping("/getAllExpItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseItemModel> getAllExpItems(){
        List<ExpenseItemModel> result;
        result = dao.getAllExpItems();

        return result;
    }

    @GetMapping("/getExpItem")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ExpenseItemModel> getExpItemById(@QueryParam("id") int id){
        List<ExpenseItemModel> result;

        result = dao.getExpItemById(id);

        return result;
    }

    @PostMapping("/addExpItemRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addExpItemReturnId(@RequestBody ExpenseItemModel expItem) {

        //Check to see if account type is credit
        expItem = logic.setPaidWithCredit(expItem);

        List<ReturnIdModel> returnedId = dao.addExpItemReturnId(expItem);
        return returnedId;
    }
}
