package in.voltforge.api.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Getter
@Configuration
public class VoltforgeAiConfig {

    @Value("${app.ai.model.url:http://localhost:2002/voltForge-ai}")
    private String modelUrl;

    @Value("${app.ai.model.timeout:15}")
    private int timeoutSeconds;

    @Value("${app.ai.model.api-token:}")
    private String apiToken;

    @Bean("voltforgeAiWebClient")
    public WebClient voltforgeAiWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(modelUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
        addPrivateServiceToken(builder);
        return builder.build();
    }

    /** Longer timeout for SSE streaming connections (token-by-token chat). */
    @Bean("voltforgeAiStreamingClient")
    public WebClient voltforgeAiStreamingClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(modelUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
        addPrivateServiceToken(builder);
        return builder.build();
    }

    private void addPrivateServiceToken(WebClient.Builder builder) {
        if (apiToken != null && !apiToken.isBlank()) {
            builder.defaultHeader("X-Voltforge-AI-Token", apiToken);
        }
    }
}
