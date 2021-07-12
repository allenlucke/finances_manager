package com.Allen.SpringFinancesServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@SpringBootApplication
@RestController
public class SpringFinancesServerApplication {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	UserTestDao dao;

	public static void main(String[] args) {
		SpringApplication.run(SpringFinancesServerApplication.class, args);
	}


	@GetMapping("/hello")
	public String sayHello(@RequestParam(value = "myName", defaultValue = "World") String name) {
		return String.format("Hello %s!", name);
	}

//	@GetMapping("/userTest")
//	@Consumes(MediaType.APPLICATION_JSON)
//	@Produces(MediaType.APPLICATION_JSON)
//	public String userTest(@QueryParam("id") int id) {
//		String respString;
//
//		respString = dao.getUserName(id);
//		return respString;
//	}
//
//	@GetMapping("/alluserTest")
//	@Consumes(MediaType.APPLICATION_JSON)
////	@Produces(MediaType.APPLICATION_JSON)
//	public List<UserModel> getAllEmployees(){
//		String sql = "SELECT * FROM \"users\";";
//		List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
//
//		List<UserModel> result = new ArrayList<UserModel>();
//		for(Map<String, Object> row:rows){
//			UserModel usr = new UserModel();
//			usr.setId((int)row.get("id"));
//			usr.setFirstName((String)row.get("firstName"));
//			result.add(usr);
//		}
//
//		return result;
//	}
}
