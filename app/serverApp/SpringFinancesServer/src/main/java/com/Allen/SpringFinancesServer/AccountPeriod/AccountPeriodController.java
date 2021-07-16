package com.Allen.SpringFinancesServer.AccountPeriod;

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
public class AccountPeriodController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountPeriodDao dao;

    @GetMapping("/getAllAcctPeriods")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountPeriodModel> getAllAccountPeriods(){
        List<AccountPeriodModel> result;
        result = dao.getAllAccountPeriods();

        return result;
    }

    @GetMapping("/getAcctPeriod")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountPeriodModel> getAcctPeriodById(@QueryParam("id") int id){
        List<AccountPeriodModel> result;

        result = dao.getAcctPeriodById(id);

        return result;
    }

    @PostMapping("/addAccountPeriodRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<ReturnIdModel> addAcctPeriodReturningId(@RequestBody AccountPeriodModel acctPeriod) {
        List<ReturnIdModel> returnedId = dao.addAcctPeriodReturningId(acctPeriod);
        return returnedId;
    }
}
