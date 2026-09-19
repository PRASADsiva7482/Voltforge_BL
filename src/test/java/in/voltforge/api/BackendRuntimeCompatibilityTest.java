package in.voltforge.api;

import in.voltforge.api.ai.dto.AiChatRequest;
import in.voltforge.api.auth.service.IdentityAvailabilityService;
import in.voltforge.api.common.dto.ApiResponse;
import in.voltforge.api.project.entity.Project;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual web server and persistence wiring with an isolated database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:runtime-compatibility;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.address=127.0.0.1",
        "logging.level.root=WARN",
        "logging.level.org.springframework=WARN"
})
class BackendRuntimeCompatibilityTest {
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired ProjectRepository projects;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean IdentityAvailabilityService identityAvailability;

    @Test
    void healthAndOpenApiAreAvailableOnTheConfiguredContextPath() throws Exception {
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(health.body()).path("status").asString()).isEqualTo("UP");

        HttpResponse<String> docs = get("/api-docs");
        assertThat(docs.statusCode()).isEqualTo(200);
        JsonNode spec = mapper.readTree(docs.body());
        assertThat(spec.path("openapi").asString()).startsWith("3.");
        assertThat(spec.path("paths").has("/api/v1/auth/me")).isTrue();
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
    }

    @Test
    void protectedEndpointsStillRequireAuthentication() throws Exception {
        assertThat(get("/api/v1/auth/me").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/admin/dashboard").statusCode()).isEqualTo(401);
    }

    @Test
    void jsonKeepsIsoDatesNullOmissionAndServerOwnedIdentity() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 9, 14, 12, 30);
        ApiResponse<Map<String, Object>> response = ApiResponse.success(Map.of("count", 1));
        response.setTimestamp(timestamp);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(response));
        assertThat(json.path("timestamp").asString()).isEqualTo("2026-09-14T12:30:00");
        assertThat(json.has("errorCode")).isFalse();
        assertThat(json.path("success").asBoolean()).isTrue();

        AiChatRequest request = mapper.readValue("""
                {"message":"Explain this circuit","authenticatedUserId":"forged",
                 "futureField":true,"history":[{"role":"user","content":"hello","extra":1}]}
                """, AiChatRequest.class);
        assertThat(request.getAuthenticatedUserId()).isNull();
        assertThat(request.getHistory()).hasSize(1);
        request.setAuthenticatedUserId("server-user");
        assertThat(mapper.readTree(mapper.writeValueAsString(request)).has("authenticatedUserId")).isFalse();
    }

    @Test
    @Transactional
    void projectJsonAndAuditingSurviveAPersistenceRoundTrip() {
        User owner = users.saveAndFlush(User.builder()
                .keycloakId("runtime-owner").username("runtime-owner")
                .email("runtime-owner@example.invalid").build());
        Map<String, Object> canvas = Map.of(
                "components", List.of(Map.of("id", "led-1", "type", "LED_STANDARD")),
                "wires", List.of());
        Project project = projects.saveAndFlush(Project.builder()
                .owner(owner).name("Runtime compatibility")
                .canvasLayout(canvas).componentConfig(Map.of("ledColor", "blue")).build());
        String projectId = project.getId();
        entityManager.clear();

        Project loaded = projects.findById(projectId).orElseThrow();
        assertThat(loaded.getCanvasLayout()).isEqualTo(canvas);
        assertThat(loaded.getComponentConfig()).containsEntry("ledColor", "blue");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getOwner().getId()).isEqualTo(owner.getId());
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/voltForge-app" + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
