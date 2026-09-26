package in.voltforge.api.config;

import lombok.Getter;
import jakarta.annotation.PostConstruct;
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

    @Value("${app.ai.model.url}")
    private String modelUrl;

    @Value("${app.ai.model.timeout}")
    private int timeoutSeconds = 15;

    @Value("${app.ai.model.streaming-timeout}")
    private int streamingTimeoutSeconds = 60;

    @Value("${app.ai.model.api-token}")
    private String apiToken;

    @Value("${app.ai.environment}")
    private String environment = "development";

    @Value("${app.ai.gateway.max-request-bytes}")
    private int maxRequestBytes = 2_000_000;

    @Value("${app.ai.gateway.max-concurrent-streams-per-user}")
    private int maxConcurrentStreamsPerUser = 2;

    @Value("${app.ai.gateway.max-concurrent-streams-per-project}")
    private int maxConcurrentStreamsPerProject = 1;

    @PostConstruct
    void validateProductionSecurityConfiguration() {
        if ("production".equalsIgnoreCase(environment)
                && (apiToken == null || apiToken.trim().length() < 32)) {
            throw new IllegalStateException(
                    "app.ai.model.api-token must contain at least 32 characters in production");
        }
    }

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
                .responseTimeout(Duration.ofSeconds(effectiveStreamingTimeoutSeconds()));

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

    private int effectiveStreamingTimeoutSeconds() {
        return Math.max(1, Math.min(streamingTimeoutSeconds, 120));
    }
}
