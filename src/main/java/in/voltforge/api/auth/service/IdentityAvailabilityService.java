package in.voltforge.api.auth.service;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Bounded, shared probe of the configured issuer. No caller-supplied URLs or credentials. */
@Service
public class IdentityAvailabilityService {
    private final String issuer;
    private final Mono<Boolean> availability;

    public IdentityAvailabilityService(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        this.issuer = issuer.replaceAll("/+$", "");
        WebClient client = WebClient.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(128 * 1024))
                .build();
        availability = client.get()
                .uri(this.issuer + "/.well-known/openid-configuration")
                .header("Accept", "application/json")
                .header("ngrok-skip-browser-warning", "true")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> this.issuer.equals(body.path("issuer").asString())
                        && body.path("authorization_endpoint").asString().startsWith(this.issuer + "/")
                        && body.path("jwks_uri").asString().startsWith(this.issuer + "/"))
                .defaultIfEmpty(false)
                .timeout(Duration.ofSeconds(2))
                .onErrorReturn(false)
                // Concurrent clicks share one bounded request, including failures.
                .cache(Duration.ofSeconds(2));
    }

    public String issuer() { return issuer; }

    public boolean isAvailable() { return Boolean.TRUE.equals(availability.block()); }
}
