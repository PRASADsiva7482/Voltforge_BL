package in.voltforge.api;

import in.voltforge.api.ai.dto.AiChatRequest;
import in.voltforge.api.auth.service.IdentityAvailabilityService;
import in.voltforge.api.common.dto.ApiResponse;
import in.voltforge.api.project.entity.Project;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.project.repository.ProjectShareRepository;
import in.voltforge.api.project.entity.ProjectShare;
import in.voltforge.api.project.service.ProjectService;
import in.voltforge.api.project.controller.CollaborationController;
import in.voltforge.api.project.dto.CanvasEvent;
import in.voltforge.api.project.dto.CreateProjectRequest;
import in.voltforge.api.project.dto.UpdateProjectRequest;
import in.voltforge.api.project.dto.CodeFileRequest;
import in.voltforge.api.project.controller.ProjectRequestBodyAdvice;
import in.voltforge.api.common.enums.SharePermission;
import org.springframework.messaging.MessagingException;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletContext;
import jakarta.websocket.server.ServerContainer;
import in.voltforge.api.config.WebSocketConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
    @Autowired ServletContext servletContext;
    @Autowired ProjectShareRepository shares;
    @Autowired ProjectService projectService;
    @Autowired CollaborationController collaboration;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean IdentityAvailabilityService identityAvailability;

    @Test
    void oversizedProjectBodiesAreRejectedBeforeControllerForKnownAndChunkedLengths() throws Exception {
        when(jwtDecoder.decode("size-token")).thenReturn(Jwt.withTokenValue("size-token")
                .header("alg", "RS256").subject("size-user").build());
        String body = "{\"name\":\"size test\",\"description\":\"" + "Ω".repeat(ProjectRequestBodyAdvice.MAX_REQUEST_BYTES / 2) + "\"}";
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (boolean chunked : new boolean[]{false, true}) {
                var publisher = chunked
                        ? HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(bytes))
                        : HttpRequest.BodyPublishers.ofByteArray(bytes);
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/voltForge-app/api/v1/projects"))
                        .header("Authorization", "Bearer size-token").header("Content-Type", "application/json")
                        .POST(publisher).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(413);
                assertThat(mapper.readTree(response.body()).path("errorCode").asString()).isEqualTo("PROJECT_PAYLOAD_TOO_LARGE");
            }
        }
        assertThat(users.findByKeycloakId("size-user")).isEmpty();
    }

    @Test
    void largeDocumentsAndCodeOnlySavesCommitWithDurableRevisionAcknowledgements() {
        User owner = users.saveAndFlush(User.builder().keycloakId("large-owner")
                .username("large-owner").email("large-owner@example.invalid").build());
        String id = null;
        try {
            var created = projectService.createProject("large-owner", CreateProjectRequest.builder().name("Large save").build());
            id = created.getId();
            var canvas = Map.<String, Object>of("notes", "Ω".repeat(550000));
            var pcb = Map.<String, Object>of("pcbLayout", Map.of("notes", "x".repeat(600000)));
            var files = List.of(CodeFileRequest.builder().filename("sketch.ino").content("// " + "漢".repeat(200000)).build());
            var saved = projectService.updateProject(id, "large-owner", UpdateProjectRequest.builder()
                    .expectedRevision(created.getDocumentRevision()).canvasLayout(canvas).componentConfig(pcb).codeFiles(files).build());
            var reopened = projectService.getProject(id, "large-owner");
            assertThat(reopened.getCanvasLayout()).isEqualTo(canvas);
            assertThat(reopened.getComponentConfig()).isEqualTo(pcb);
            assertThat(reopened.getCodeFiles().getFirst().getContent()).isEqualTo(files.getFirst().getContent());
            assertThat(reopened.getDocumentRevision()).isEqualTo(saved.getDocumentRevision()).isEqualTo("1");
            var codeOnly = projectService.updateProject(id, "large-owner", UpdateProjectRequest.builder()
                    .expectedRevision("1").codeFiles(List.of(CodeFileRequest.builder().filename("sketch.ino").content("// code only").build())).build());
            assertThat(codeOnly.getDocumentRevision()).isEqualTo("2");
            var noop = projectService.updateProject(id, "large-owner", UpdateProjectRequest.builder().expectedRevision("2").build());
            assertThat(noop.getDocumentRevision()).isEqualTo("3");
            assertThat(projectService.getProject(id, "large-owner").getDocumentRevision()).isEqualTo("3");
        } finally {
            if (id != null) projectService.deleteProject(id, "large-owner");
            users.deleteById(owner.getId());
        }
    }

    @Test
    void concurrentVersionedWritesRollBackTheLosingChildDocument() throws Exception {
        User owner = users.saveAndFlush(User.builder().keycloakId("race-owner")
                .username("race-owner").email("race-owner@example.invalid").build());
        var created = projectService.createProject("race-owner", CreateProjectRequest.builder().name("Atomic race").build());
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(writer -> executor.submit(() -> {
                try {
                    return new TransactionTemplate(transactionManager).execute(status -> {
                        Project project = projects.findById(created.getId()).orElseThrow();
                        project.getCodeFiles().size();
                        try { barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                        catch (Exception e) { throw new RuntimeException(e); }
                        project.setDescription("writer-" + writer);
                        project.getCodeFiles().getFirst().setContent("writer-" + writer);
                        projects.flush();
                        return "saved";
                    });
                } catch (org.springframework.dao.OptimisticLockingFailureException e) { return "conflict"; }
            })).toList();
            assertThat(List.of(tasks.get(0).get(), tasks.get(1).get())).containsExactlyInAnyOrder("saved", "conflict");
            var reopened = projectService.getProject(created.getId(), "race-owner");
            assertThat(reopened.getDocumentRevision()).isEqualTo("1");
            assertThat(reopened.getCodeFiles().getFirst().getContent()).isEqualTo(reopened.getDescription());
        } finally {
            projectService.deleteProject(created.getId(), "race-owner");
            users.deleteById(owner.getId());
        }
    }

    @Test
    void nativeWebSocketContainerAcceptsTheConfiguredStompFrameBudget() {
        ServerContainer container = (ServerContainer) servletContext.getAttribute("jakarta.websocket.server.ServerContainer");
        assertThat(container).isNotNull();
        assertThat(container.getDefaultMaxTextMessageBufferSize()).isEqualTo(WebSocketConfig.CONTAINER_BUFFER_SIZE);
        assertThat(container.getDefaultMaxBinaryMessageBufferSize()).isEqualTo(WebSocketConfig.CONTAINER_BUFFER_SIZE);
    }

    @Test
    @Transactional
    void collaborationRetainsSharedPermissionsAndServerOwnedEventIdentity() {
        User owner = users.saveAndFlush(User.builder().keycloakId("sync-owner")
                .username("sync-owner").email("sync-owner@example.invalid").build());
        User viewer = users.saveAndFlush(User.builder().keycloakId("sync-viewer")
                .username("sync-viewer").email("sync-viewer@example.invalid").build());
        Project project = projects.saveAndFlush(Project.builder().owner(owner).name("Live Sync permissions").build());
        ProjectShare share = shares.saveAndFlush(ProjectShare.builder().project(project).sharedWithUser(viewer)
                .permission(SharePermission.VIEW).build());
        assertThat(projectService.canAccessProject(project.getId(), "sync-viewer")).isTrue();
        assertThat(projectService.canEditProject(project.getId(), "sync-viewer")).isFalse();
        CanvasEvent event = new CanvasEvent();
        event.setEventType("CANVAS_SYNC");
        event.setUserId("forged-owner");
        event.setPayload(Map.of("nodes", List.of(), "wires", List.of()));
        assertThatThrownBy(() -> collaboration.handleCanvasUpdate(project.getId(), event, () -> "sync-viewer"))
                .isInstanceOf(MessagingException.class);
        share.setPermission(SharePermission.EDIT);
        shares.saveAndFlush(share);
        assertThat(projectService.canEditProject(project.getId(), "sync-viewer")).isTrue();
        collaboration.handleCanvasUpdate(project.getId(), event, () -> "sync-viewer");
        assertThat(event.getUserId()).isEqualTo("sync-viewer");
        assertThat(projects.findById(project.getId()).orElseThrow().getDocumentRevision()).isZero();
    }

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
