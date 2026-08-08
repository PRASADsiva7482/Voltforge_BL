package in.voltforge.api.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes Spring Boot's auto-configured ObjectMapper instead of replacing it.
 * <p>
 * Using {@link Jackson2ObjectMapperBuilderCustomizer} ensures that all
 * {@code spring.jackson.*} properties from application.yml are respected,
 * including {@code fail-on-unknown-properties: false}.
 * <p>
 * A manual {@code new ObjectMapper()} bean would silently bypass Spring Boot's
 * auto-configuration and ignore all YAML-based Jackson settings.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer voltforgeJacksonCustomizer() {
        return builder -> builder
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.FAIL_ON_EMPTY_BEANS,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
                );
    }
}
