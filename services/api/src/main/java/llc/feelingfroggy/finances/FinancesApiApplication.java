package llc.feelingfroggy.finances;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinancesApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinancesApiApplication.class, args);
    }
}
