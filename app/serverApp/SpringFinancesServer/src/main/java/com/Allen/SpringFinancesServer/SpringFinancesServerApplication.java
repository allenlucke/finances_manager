package com.Allen.SpringFinancesServer;

import com.Allen.SpringFinancesServer.User.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;


@SpringBootApplication
@RestController
//@CrossOrigin("http://localhost:4200")
public class SpringFinancesServerApplication {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	UserDao dao;

	private static final String CLASS_NAME = "SpringFinancesServerApplication --- ";
	private static final String METHOD_ENTERING = "Entering:  ";
	private static final String METHOD_EXITING = "Exiting:  ";

	public static final Logger LOGGER = LoggerFactory.getLogger(SpringFinancesServerApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(SpringFinancesServerApplication.class, args);

		final String methodName = "main() ";

		LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);
	}


	@GetMapping("/hello")
	@Consumes(MediaType.APPLICATION_JSON)
	public ResponseEntity sayHello(@RequestParam(value = "myName", defaultValue = "World") String name) {
		final String methodName = "sayHello() ";
		LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);

		SayHelloModel hello = new SayHelloModel();
		hello.setHelloText("Hello "+ name);

		LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
		return new ResponseEntity(hello, HttpStatus.OK);
	}

}
