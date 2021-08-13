package com.Allen.SpringFinancesServer.IncomeItem;
;
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
public class IncomeItemController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    IncomeItemDao dao;

    @GetMapping("/getAllIncomeItems")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeItemModel> getAllIncomeItems(){
        List<IncomeItemModel> result;
        result = dao.getAllIncomeItems();

        return result;
    }

    @GetMapping("/getIncomeItemById")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<IncomeItemModel> getIncomeItemById(@QueryParam("id") int id){
        List<IncomeItemModel> result;

        result = dao.getIncomeItemById(id);

        return result;
    }

    @PostMapping("/addIncomeItemReturnId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addIncomeItemReturnId(@RequestBody IncomeItemModel expItem) {

        //Check to see if account type is credit
//        expItem = logic.setPaidWithCredit(expItem);

        List<ReturnIdModel> returnedId = dao.addIncomeItemReturnId(expItem);
        return returnedId;
    }
}
