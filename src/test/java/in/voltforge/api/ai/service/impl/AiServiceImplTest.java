package in.voltforge.api.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import in.voltforge.api.ai.dto.AiChatRequest;
import in.voltforge.api.ai.dto.AiChatResponse;
import in.voltforge.api.ai.gateway.AiGatewayPolicy;
import in.voltforge.api.ai.gateway.AiRequestAdmission;
import in.voltforge.api.config.VoltforgeAiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceImplTest {

    @Test
    void preservesTypedRelayOrderingCitationsProposalsCompletionAndErrors() throws Exception {
        AtomicReference<Integer> calls = new AtomicReference<>(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/voltForge-ai/api/v1/model/chat/stream", exchange -> {
            String response;
            if (calls.getAndSet(calls.get() + 1) == 0) {
                response = "event: start\ndata: {\"type\":\"start\"}\n\n"
                        + "event: tool\ndata: {\"type\":\"tool\",\"toolName\":\"tool:engineering\"}\n\n"
                        + "event: citation\ndata: {\"type\":\"citation\",\"citationId\":\"citation:local:1\"}\n\n"
                        + "event: uncertainty\ndata: {\"type\":\"uncertainty\",\"level\":\"low\"}\n\n"
                        + "event: delta\ndata: {\"type\":\"delta\",\"delta\":\"Use 1k.\"}\n\n"
                        + "event: proposal\ndata: {\"type\":\"proposal\",\"proposalId\":\"proposal:1\"}\n\n"
                        + "event: complete\ndata: {\"type\":\"complete\",\"reply\":\"Done\"}\n\n";
            } else {
                response = "event: start\ndata: {\"type\":\"start\"}\n\n"
                        + "event: error\ndata: {\"type\":\"error\",\"code\":\"LOCAL_GENERATION_FAILED\"}\n\n";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        try {
            VoltforgeAiConfig config = testConfig(server);
            AiServiceImpl service = new AiServiceImpl(
                    config.voltforgeAiWebClient(), config.voltforgeAiStreamingClient(), config,
                    new ObjectMapper());
            AiChatRequest request = AiChatRequest.builder()
                    .message("Explain the circuit")
                    .authenticatedUserId("jwt-user-027")
                    .projectId("project-027")
                    .sessionId("session-027")
                    .build();

            List<ServerSentEvent<String>> completeEvents = service.chatStream(request)
                    .collectList().block(Duration.ofSeconds(5));
            List<ServerSentEvent<String>> errorEvents = service.chatStream(request)
                    .collectList().block(Duration.ofSeconds(5));

            assertThat(completeEvents).extracting(ServerSentEvent::event)
                    .containsExactly("start", "tool", "citation", "uncertainty", "delta", "proposal", "complete");
            assertThat(completeEvents.get(2).data()).contains("citation:local:1");
            assertThat(completeEvents.get(5).data()).contains("proposal:1");
            assertThat(completeEvents.get(6).data()).contains("complete");
            assertThat(errorEvents).extracting(ServerSentEvent::event)
                    .containsExactly("start", "error");
            assertThat(errorEvents.get(1).data()).contains("LOCAL_GENERATION_FAILED");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void releasesGatewayAdmissionWhenTheBrowserCancelsAStream() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/voltForge-ai/api/v1/model/chat/stream", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(
                        "event: start\ndata: {\"type\":\"start\"}\n\n".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                Thread.sleep(1_000);
            } catch (Exception ignored) {
                // The client cancellation is expected to close this response.
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            VoltforgeAiConfig config = testConfig(server);
            ReflectionTestUtils.setField(config, "maxConcurrentStreamsPerUser", 1);
            AiRequestAdmission admission = new AiRequestAdmission(config);
            AiServiceImpl service = new AiServiceImpl(
                    config.voltforgeAiWebClient(), config.voltforgeAiStreamingClient(), config,
                    new ObjectMapper(), new AiGatewayPolicy(new ObjectMapper(), config), admission);
            AiChatRequest request = AiChatRequest.builder()
                    .message("Cancel this")
                    .authenticatedUserId("jwt-user-cancel")
                    .projectId("project-cancel")
                    .sessionId("session-cancel")
                    .build();

            service.chatStream(request).take(1).blockLast(Duration.ofSeconds(5));

            assertThat(admission.activeStreamsForUser("jwt-user-cancel")).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesUpstreamEventNamesAndForwardsPrivateServiceToken() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        AtomicReference<String> receivedUser = new AtomicReference<>();
        AtomicReference<String> receivedProject = new AtomicReference<>();
        AtomicReference<String> receivedSession = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/voltForge-ai/api/v1/model/chat/stream", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Voltforge-AI-Token"));
            receivedUser.set(exchange.getRequestHeaders().getFirst("X-Voltforge-User-Id"));
            receivedProject.set(exchange.getRequestHeaders().getFirst("X-Voltforge-Project-Id"));
            receivedSession.set(exchange.getRequestHeaders().getFirst("X-Voltforge-Session-Id"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("event: start\n"
                    + "data: {\"type\":\"start\",\"sessionId\":\"session-1\"}\n\n"
                    + "event: delta\n"
                    + "data: {\"type\":\"delta\",\"delta\":\"Use a resistor.\"}\n\n")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            VoltforgeAiConfig config = new VoltforgeAiConfig();
            ReflectionTestUtils.setField(
                    config,
                    "modelUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/voltForge-ai"
            );
            ReflectionTestUtils.setField(config, "timeoutSeconds", 5);
            ReflectionTestUtils.setField(config, "apiToken", "private-test-token");

            AiServiceImpl service = new AiServiceImpl(
                    config.voltforgeAiWebClient(),
                    config.voltforgeAiStreamingClient(),
                    config,
                    new ObjectMapper()
            );
            AiChatRequest request = AiChatRequest.builder()
                    .message("Explain the LED circuit")
                    .authenticatedUserId("jwt-user-25")
                    .projectId("project-25")
                    .sessionId("session-25")
                    .memory(List.of(Map.of("content", "forged client memory")))
                    .build();

            List<ServerSentEvent<String>> events = service.chatStream(request)
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertThat(events).isNotNull();
            assertThat(events).extracting(ServerSentEvent::event)
                    .containsExactly("start", "delta");
            assertThat(events.get(1).data()).contains("Use a resistor.");
            assertThat(receivedToken.get()).isEqualTo("private-test-token");
            assertThat(receivedUser.get()).isEqualTo("jwt-user-25");
            assertThat(receivedProject.get()).isEqualTo("project-25");
            assertThat(receivedSession.get()).isEqualTo("session-25");
            assertThat(receivedBody.get()).contains("\"memory\":[]");
            assertThat(receivedBody.get()).contains("\"schemaVersion\":1");
            assertThat(receivedBody.get()).contains("\"contractVersion\":\"1.0.0\"");
            assertThat(receivedBody.get()).doesNotContain("forged client memory");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesTypedInternetRetrievalMetadata() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AiChatResponse response = mapper.readValue(
                """
                {
                  "schemaVersion": 1,
                  "contractVersion": "1.0.0",
                  "requestId": "request-026",
                  "sessionId": "session-026",
                  "projectRevision": "client:revision-026",
                  "model": "voltforge-local-engine-v1",
                  "mode": "deterministic-fallback",
                  "artifact": {
                    "artifactVersion": "deterministic-tools-v1",
                    "runtimeState": "unavailable",
                    "ready": false
                  },
                  "readiness": {
                    "localModel": {"ready": false},
                    "engineeringTools": {"ready": true}
                  },
                  "reply": "Local processing continued.",
                  "hasCode": false,
                  "internetRetrieval": {
                    "policyId": "vfai023-secure-internet-evidence-v1",
                    "status": "degraded",
                    "reasonCode": "PROVIDER_REQUEST_FAILED",
                    "generationDependency": false,
                    "trainingUseAllowed": false
                  }
                }
                """,
                AiChatResponse.class
        );

        assertThat(response.getInternetRetrieval())
                .containsEntry("policyId", "vfai023-secure-internet-evidence-v1")
                .containsEntry("status", "degraded")
                .containsEntry("generationDependency", false)
                .containsEntry("trainingUseAllowed", false);
        assertThat(response.getSchemaVersion()).isEqualTo(1);
        assertThat(response.getContractVersion()).isEqualTo("1.0.0");
        assertThat(response.getRequestId()).isEqualTo("request-026");
        assertThat(response.getProjectRevision()).isEqualTo("client:revision-026");
        assertThat(response.getMode()).isEqualTo("deterministic-fallback");
        assertThat(response.getArtifact())
                .containsEntry("artifactVersion", "deterministic-tools-v1")
                .containsEntry("ready", false);
        assertThat(response.getReadiness()).containsKeys("localModel", "engineeringTools");
    }

    @Test
    void preservesTypedGroundingAndEvidenceClasses() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AiChatResponse response = mapper.readValue(
                """
                {
                  "reply": "Pin D3 supports PWM.",
                  "hasCode": false,
                  "citations": [{
                    "citationId": "citation:local:uno-pin-d3",
                    "evidenceKind": "local",
                    "authority": "retrieved",
                    "title": "Arduino UNO pin map",
                    "exactModelEvidence": true,
                    "evidenceRefs": ["evidence:1:abc"]
                  }],
                  "grounding": {
                    "policyId": "vfai024-claim-grounding-v1",
                    "status": "grounded",
                    "supportedClaimCount": 1,
                    "maximumConfidence": 0.95
                  }
                }
                """,
                AiChatResponse.class
        );

        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getCitations().get(0))
                .containsEntry("evidenceKind", "local")
                .containsEntry("exactModelEvidence", true);
        assertThat(response.getGrounding())
                .containsEntry("policyId", "vfai024-claim-grounding-v1")
                .containsEntry("status", "grounded")
                .containsEntry("supportedClaimCount", 1);
    }

    @Test
    void preservesTypedBoundedMemoryMetadata() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AiChatResponse response = mapper.readValue(
                """
                {
                  "reply": "Local processing continued.",
                  "hasCode": false,
                  "memory": {
                    "policyId": "vfai025-bounded-memory-v1",
                    "status": "ready",
                    "enabled": true,
                    "selectedEntryCount": 2,
                    "trainingUseAllowed": false,
                    "rawIdentifiersStored": false
                  }
                }
                """,
                AiChatResponse.class
        );

        assertThat(response.getMemory())
                .containsEntry("policyId", "vfai025-bounded-memory-v1")
                .containsEntry("status", "ready")
                .containsEntry("enabled", true)
                .containsEntry("trainingUseAllowed", false)
                .containsEntry("rawIdentifiersStored", false);
    }

    @Test
    void forwardsAuthenticatedScopeToMemoryManagementEndpoint() throws Exception {
        AtomicReference<String> receivedUser = new AtomicReference<>();
        AtomicReference<String> receivedProject = new AtomicReference<>();
        AtomicReference<String> receivedSession = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/voltForge-ai/api/v1/model/memory", exchange -> {
            receivedUser.set(exchange.getRequestHeaders().getFirst("X-Voltforge-User-Id"));
            receivedProject.set(exchange.getRequestHeaders().getFirst("X-Voltforge-Project-Id"));
            receivedSession.set(exchange.getRequestHeaders().getFirst("X-Voltforge-Session-Id"));
            byte[] response = "{\"enabled\":true,\"entryCount\":0,\"trainingUseAllowed\":false}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            VoltforgeAiConfig config = new VoltforgeAiConfig();
            ReflectionTestUtils.setField(config, "modelUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/voltForge-ai");
            ReflectionTestUtils.setField(config, "timeoutSeconds", 5);
            ReflectionTestUtils.setField(config, "apiToken", "private-test-token");
            AiServiceImpl service = new AiServiceImpl(
                    config.voltforgeAiWebClient(), config.voltforgeAiStreamingClient(),
                    config, new ObjectMapper());

            Map<String, Object> memory = service.inspectMemory(
                    "jwt-user-memory", "project-memory", "session-memory", "revision-1");

            assertThat(memory)
                    .containsEntry("enabled", true)
                    .containsEntry("trainingUseAllowed", false);
            assertThat(receivedUser.get()).isEqualTo("jwt-user-memory");
            assertThat(receivedProject.get()).isEqualTo("project-memory");
            assertThat(receivedSession.get()).isEqualTo("session-memory");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void forwardsHardwareCoverageAsAReadOnlyTypedMap() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/voltForge-ai/api/v1/model/hardware-coverage", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Voltforge-AI-Token"));
            byte[] response = "{\"reportId\":\"vfai-fu-001-ui-hardware-coverage\",\"entryCount\":49}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            VoltforgeAiConfig config = testConfig(server);
            AiServiceImpl service = new AiServiceImpl(
                    config.voltforgeAiWebClient(), config.voltforgeAiStreamingClient(),
                    config, new ObjectMapper());

            Map<String, Object> coverage = service.getHardwareCoverage();

            assertThat(coverage)
                    .containsEntry("reportId", "vfai-fu-001-ui-hardware-coverage")
                    .containsEntry("entryCount", 49);
            assertThat(receivedToken.get()).isEqualTo("private-test-token");
        } finally {
            server.stop(0);
        }
    }

    private VoltforgeAiConfig testConfig(HttpServer server) {
        VoltforgeAiConfig config = new VoltforgeAiConfig();
        ReflectionTestUtils.setField(
                config,
                "modelUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/voltForge-ai"
        );
        ReflectionTestUtils.setField(config, "timeoutSeconds", 5);
        ReflectionTestUtils.setField(config, "streamingTimeoutSeconds", 5);
        ReflectionTestUtils.setField(config, "apiToken", "private-test-token");
        return config;
    }
}
