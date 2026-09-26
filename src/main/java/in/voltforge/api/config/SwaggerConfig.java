package in.voltforge.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${keycloak.auth-server-url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;


    @Bean
    public OpenAPI voltforgeOpenAPI() {
        String authUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect";

        return new OpenAPI()
                .info(new Info()
                        .title("VoltForge API")
                        .description("VoltForge — Browser-based Virtual Electronics Simulator Platform API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("VoltForge Team")
                                .email("support@voltforge.in"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://voltforge.in")))
                .addSecurityItem(new SecurityRequirement().addList("keycloak"))
                .components(new Components()
                        .addSecuritySchemes("keycloak", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Keycloak OAuth2 Authentication")
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(authUrl + "/auth")
                                                .tokenUrl(authUrl + "/token")
                                        )
                                )
                        )
                        .addSecuritySchemes("bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer Token")
                        )
                );
    }
}
