package in.voltforge.api.ai.document;

import com.sun.net.httpserver.HttpServer;
import in.voltforge.api.config.VoltforgeAiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentEmbeddingClientTest {
    @Test void acceptsActualEncoderMetadataContract() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/voltForge-ai/api/v1/model/embeddings",exchange->{
            byte[] body=("{\"modelId\":\""+"a".repeat(64)+"\",\"dimensions\":384,\"maxTokens\":256,\"available\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var config=new VoltforgeAiConfig();
            ReflectionTestUtils.setField(config,"modelUrl","http://127.0.0.1:"+server.getAddress().getPort()+"/voltForge-ai");
            var client=new DocumentEmbeddingClient(config.voltforgeAiWebClient());
            assertThat(client.model()).isEqualTo(new DocumentEmbeddingClient.Model("a".repeat(64),384,true));
        } finally { server.stop(0); }
    }
}
