package in.voltforge.api.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import in.voltforge.api.ai.dto.AiChatRequest;
import in.voltforge.api.config.VoltforgeAiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceImplTest {

    @Test
    void preservesUpstreamEventNamesAndForwardsPrivateServiceToken() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/voltForge-ai/api/v1/model/chat/stream", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Voltforge-AI-Token"));
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
                    .build();

            List<ServerSentEvent<String>> events = service.chatStream(request)
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertThat(events).isNotNull();
            assertThat(events).extracting(ServerSentEvent::event)
                    .containsExactly("start", "delta");
            assertThat(events.get(1).data()).contains("Use a resistor.");
            assertThat(receivedToken.get()).isEqualTo("private-test-token");
        } finally {
            server.stop(0);
        }
    }
}
