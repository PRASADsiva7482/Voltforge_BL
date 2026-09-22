package in.voltforge.api.config;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customizes Spring Boot's auto-configured JsonMapper instead of replacing it.
 * <p>
 * Using {@link JsonMapperBuilderCustomizer} ensures that all
 * {@code spring.jackson.*} properties from application.yml are respected,
 * including {@code fail-on-unknown-properties: false}.
 * <p>
 * A manual {@code new JsonMapper()} bean would silently bypass Spring Boot's
 * auto-configuration and ignore all YAML-based Jackson settings.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer voltforgeJacksonCustomizer() {
        return builder -> builder
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    }
}
