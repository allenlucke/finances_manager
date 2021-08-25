package com.Allen.SpringFinancesServer;

import com.Allen.SpringFinancesServer.User.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@SpringBootApplication
@RestController
public class SpringFinancesServerApplication {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	UserDao dao;

	public static void main(String[] args) {
		SpringApplication.run(SpringFinancesServerApplication.class, args);
	}


	@GetMapping("/hello")
	public String sayHello(@RequestParam(value = "myName", defaultValue = "World") String name) {
		return String.format("Hello %s!", name);
	}

}
