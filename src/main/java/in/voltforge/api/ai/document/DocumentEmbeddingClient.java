package in.voltforge.api.ai.document;

import in.voltforge.api.ai.gateway.AiGatewayException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.*;

@Component
public class DocumentEmbeddingClient {
    private final WebClient client;
    public record Model(String modelId,int dimensions,boolean available) {}
    public record Encoded(String modelId,int dimensions,List<List<Double>> vectors) {}
    public DocumentEmbeddingClient(@Qualifier("voltforgeAiWebClient") WebClient client) { this.client=client; }
    public Model model() {
        try {
            var model=client.get().uri("/api/v1/model/embeddings").retrieve().bodyToMono(Model.class).block(Duration.ofSeconds(5));
            if(model==null||!model.available()||model.dimensions()!=384||model.modelId()==null||!model.modelId().matches("[a-f0-9]{64}")) throw new IllegalStateException();
            return model;
        } catch(Exception e) { throw unavailable(); }
    }
    public List<List<Double>> encode(Model model,List<String> texts) {
        try {
            var response=client.post().uri("/api/v1/model/embeddings").bodyValue(Map.of("modelId",model.modelId(),"texts",texts))
                .retrieve().bodyToMono(Encoded.class).block(Duration.ofSeconds(10));
            if(response==null||!model.modelId().equals(response.modelId())||response.dimensions()!=384||response.vectors()==null||response.vectors().size()!=texts.size()) throw new IllegalStateException();
            response.vectors().forEach(DocumentHybridSearch::validateVector);
            return response.vectors();
        } catch(Exception e) { throw unavailable(); }
    }
    static AiGatewayException unavailable() { return new AiGatewayException(HttpStatus.SERVICE_UNAVAILABLE,"AI_EMBEDDING_UNAVAILABLE","Reference indexing is unavailable or busy. Retry shortly; your documents are preserved.",true); }
}
