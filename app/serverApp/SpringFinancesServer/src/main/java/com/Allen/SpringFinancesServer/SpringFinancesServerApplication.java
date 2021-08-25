package com.Allen.SpringFinancesServer;

import com.Allen.SpringFinancesServer.User.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	public String sayHello(@RequestParam(value = "myName", defaultValue = "World") String name) {
		final String methodName = "sayHello() ";
		LOGGER.info(CLASS_NAME + METHOD_ENTERING + methodName);
		LOGGER.info(CLASS_NAME + METHOD_EXITING + methodName);
		return String.format("Hello %s!", name);
	}

}
