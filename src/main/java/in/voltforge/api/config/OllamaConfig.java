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
public class OllamaConfig {

    @Value("${app.ai.ollama.base-url:http://100.122.105.63:11434}")
    private String baseUrl;

    @Value("${app.ai.ollama.model:gemma3}")
    private String model;

    @Value("${app.ai.ollama.timeout:120}")
    private int timeoutSeconds;

    @Bean("ollamaWebClient")
    public WebClient ollamaWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}
