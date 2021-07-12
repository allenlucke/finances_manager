package com.Allen.SpringFinancesServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class UserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    UserTestDao dao;

    @GetMapping("/userTest")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String userTest(@QueryParam("id") int id) {
        String respString;
        respString = dao.getUserName(id);
        return respString;
    }

    @GetMapping("/alluserTest")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<UserModel> getAllEmployees(){
        List<UserModel> result;
        result = dao.getAllEmployees();
        return result;
    }
}
