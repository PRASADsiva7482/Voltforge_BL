package in.voltforge.api;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableCaching
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "in.voltforge.api")
@EntityScan(basePackages = "in.voltforge.api")
public class VoltforgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoltforgeApplication.class, args);
    }
}
