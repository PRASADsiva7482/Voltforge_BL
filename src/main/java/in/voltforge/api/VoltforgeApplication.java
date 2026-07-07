package in.voltforge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "in.voltforge.api")
@EntityScan(basePackages = "in.voltforge.api")
public class VoltforgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoltforgeApplication.class, args);
    }
}
