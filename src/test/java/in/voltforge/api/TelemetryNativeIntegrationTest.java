package in.voltforge.api;

import in.voltforge.api.ai.observability.AiTelemetry;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="voltforge.telemetry.native", matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:telemetry_native;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false", "server.address=127.0.0.1", "logging.level.root=WARN",
    "logging.level.in.voltforge.api=WARN", "logging.level.org.springframework=WARN"
})
class TelemetryNativeIntegrationTest {
    static Process nativeProcess;
    static int nativePort;
    static int browserPort;
    static String serviceToken = UUID.randomUUID().toString();
    @Autowired UserRepository users;
    @Autowired AiTelemetry telemetry;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean in.voltforge.api.auth.service.IdentityAvailabilityService identityAvailability;
    @LocalServerPort int port;

    @DynamicPropertySource static void nativeConfiguration(DynamicPropertyRegistry registry) throws Exception {
        try (var socket = new java.net.ServerSocket(0)) { nativePort = socket.getLocalPort(); }
        try (var socket = new java.net.ServerSocket(0)) { browserPort = socket.getLocalPort(); }
        var root = Path.of("../Voltforge_AI").toAbsolutePath().normalize();
        var builder = new ProcessBuilder(root.resolve(".venv/Scripts/python.exe").toString(),
            "scripts/telemetry_probe_server.py", Integer.toString(nativePort)).directory(root.toFile());
        builder.environment().putAll(Map.of("VOLTFORGE_AI_API_TOKEN", serviceToken,
            "VOLTFORGE_AI_MAX_PENDING_REQUESTS", "0", "VOLTFORGE_AI_EMBEDDINGS_ENABLED", "false",
            "VOLTFORGE_AI_AUTO_DOWNLOAD_MODEL", "false", "VOLTFORGE_AI_MAX_TOKENS", "768",
            "VOLTFORGE_AI_WEB_SEARCH_ENABLED", "false", "VOLTFORGE_AI_N_THREADS", "4",
            "VOLTFORGE_AI_N_CTX", "4096", "VOLTFORGE_AI_ENV", "production"));
        builder.redirectErrorStream(true).redirectOutput(Path.of("target/aif-007-native-service.log").toFile());
        nativeProcess = builder.start();
        try(var client=HttpClient.newHttpClient()) {
            long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
            boolean ready = false;
            while(System.nanoTime() < deadline && nativeProcess.isAlive()) {
                try {
                    var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+nativePort+"/"))
                        .timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.discarding());
                    if(response.statusCode()==200) { ready=true; break; }
                } catch(java.io.IOException ignored) { }
                Thread.sleep(200);
            }
            if(!ready) { nativeProcess.destroyForcibly(); throw new IllegalStateException("Native probe startup failed; inspect local test log"); }
        }
        registry.add("app.ai.model.url", () -> "http://127.0.0.1:"+nativePort+"/voltForge-ai");
        registry.add("app.ai.model.api-token", () -> serviceToken);
        registry.add("app.ai.model.streaming-timeout", () -> 120);
        registry.add("app.cors.allowed-origins", () -> "http://127.0.0.1:"+browserPort);
    }
    @AfterAll static void closeNative() throws Exception {
        if(nativeProcess != null) { nativeProcess.destroy(); if(!nativeProcess.waitFor(10,TimeUnit.SECONDS)) nativeProcess.destroyForcibly(); }
    }

    @Test void browserThroughActualGatewayPersistenceAndNativeModel() throws Exception {
        users.saveAndFlush(User.builder().keycloakId("telemetry-probe").username("telemetry-probe")
            .email("telemetry-probe@example.invalid").build());
        when(jwtDecoder.decode("telemetry-probe")).thenReturn(Jwt.withTokenValue("telemetry-probe")
            .header("alg","RS256").subject("telemetry-probe").build());
        Path output = Path.of("target/aif-007-cross-app.json").toAbsolutePath();
        var builder = new ProcessBuilder("node", "scripts/run-telemetry-cross-app.mjs")
            .directory(Path.of("../Voltforge_UI").toFile());
        builder.environment().put("TELEMETRY_BACKEND", "http://127.0.0.1:"+port+"/voltForge-app");
        builder.environment().put("TELEMETRY_OUTPUT", output.toString());
        builder.environment().put("TELEMETRY_BROWSER_PORT", Integer.toString(browserPort));
        builder.redirectErrorStream(true).redirectOutput(Path.of("target/aif-007-browser-probe.log").toFile());
        var browser = builder.start();
        try {
            assertThat(browser.waitFor(5,TimeUnit.MINUTES)).as("Browser workload finished").isTrue();
            assertThat(browser.exitValue()).as("Browser native probe; inspect target/aif-007-browser-probe.log").isZero();
        } finally { if(browser.isAlive()) { browser.descendants().forEach(ProcessHandle::destroy); browser.destroyForcibly(); } }
        var mapper = new JsonMapper();
        var report = mapper.readTree(Files.readString(output));
        assertThat(((Map<?, ?>) telemetry.snapshot().get("streamErrorRate")).get("samples")).isEqualTo(5);
        assertThat(((Map<?, ?>) telemetry.snapshot().get("streamErrorRate")).get("failures")).isEqualTo(0L);
        ((tools.jackson.databind.node.ObjectNode)report).set("backend", mapper.valueToTree(telemetry.snapshot()));
        ((tools.jackson.databind.node.ObjectNode)report).set("profile", mapper.valueToTree(Map.of(
            "activeRequests",1,"queuedRequests",0,"threads",4,"contextTokens",4096,"outputTokenLimit",768,
            "sampleInterpretation","one cold then four sequential warm requests; synthetic identity and H2; no live search",
            "releaseAccepted",false)));
        try(var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+nativePort+"/__test/telemetry"))
                .header("X-Voltforge-AI-Token",serviceToken).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var nativeMetrics = mapper.readTree(response.body());
            assertThat(nativeMetrics.path("latencyMs").path("native_prefill").path("count").asInt()).isEqualTo(5);
            ((tools.jackson.databind.node.ObjectNode)report).set("native",nativeMetrics);
        }
        String sanitized = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        assertThat(sanitized).doesNotContain(serviceToken,"telemetry-probe","resistor","example.invalid");
        Files.writeString(output,sanitized);
    }
}
