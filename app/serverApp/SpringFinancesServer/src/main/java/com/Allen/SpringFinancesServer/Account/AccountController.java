package com.Allen.SpringFinancesServer.Account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RestController
public class AccountController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    AccountDao dao;

    @GetMapping("/getAllAccounts")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountModel> getAllAccounts(){
        List<AccountModel> result;
        result = dao.getAllAccounts();

        return result;
    }

    @GetMapping("/getAccount")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<AccountModel> getAccountById(@QueryParam("id") int id){
        List<AccountModel> result;

        result = dao.getAccountById(id);

        return result;
    }

}
