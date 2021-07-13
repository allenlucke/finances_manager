package com.Allen.SpringFinancesServer.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;

@RestController
public class UserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    UserDao dao;

    @GetMapping("/getUserFirstName")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String getUserFirstName(@QueryParam("id") int id) {
        String respString;
        respString = dao.getUserFirstName(id);
        return respString;
    }

    @GetMapping("/getAllUsers")
    @Consumes(MediaType.APPLICATION_JSON)
    public List<UserModel> getAllUsers(){
        List<UserModel> result;
        result = dao.getAllUsers();
        return result;
    }

    @PostMapping("/addUserRetId")
    @Consumes(MediaType.APPLICATION_JSON)
    public int addUser(@RequestBody UserModel usr) {
        int returnedId = dao.addUserReturnId(usr);
        return returnedId;
    }

    @PostMapping("/addUserRetUsr")
    @Consumes(MediaType.APPLICATION_JSON)
    public UserModel addUserRetUsr(@RequestBody UserModel usr) {
        UserModel user = dao.addUserReturnUser(usr);
        return user;
    }
}
