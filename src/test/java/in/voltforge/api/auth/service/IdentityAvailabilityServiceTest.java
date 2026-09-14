package in.voltforge.api.auth.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class IdentityAvailabilityServiceTest {
    private HttpServer server;
    private String issuer;
    private volatile String body;
    private volatile int status = 200;
    private volatile boolean hang;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/test";
        body = "{\"issuer\":\"" + issuer + "\",\"authorization_endpoint\":\"" + issuer
                + "/protocol/openid-connect/auth\",\"jwks_uri\":\"" + issuer + "/protocol/openid-connect/certs\"}";
        server.createContext("/realms/test/.well-known/openid-configuration", exchange -> {
            requests.incrementAndGet();
            if (hang) return;
            assertThat(exchange.getRequestHeaders().getFirst("ngrok-skip-browser-warning")).isEqualTo("true");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void validatesTheConfiguredIssuerAndSharesRepeatedProbes() {
        IdentityAvailabilityService service = new IdentityAvailabilityService(issuer + "/");
        assertThat(service.isAvailable()).isTrue();
        assertThat(service.isAvailable()).isTrue();
        assertThat(service.issuer()).isEqualTo(issuer);
        assertThat(requests).hasValue(1);
    }

    @Test void rejectsWrongIssuer() {
        body = body.replace("\"issuer\":\"" + issuer, "\"issuer\":\"https://wrong.invalid");
        assertThat(new IdentityAvailabilityService(issuer).isAvailable()).isFalse();
    }

    @Test void rejectsWarningHtmlAndCachesFailure() {
        body = "<html>Visit site</html>";
        IdentityAvailabilityService service = new IdentityAvailabilityService(issuer);
        assertThat(service.isAvailable()).isFalse();
        assertThat(service.isAvailable()).isFalse();
        assertThat(requests).hasValue(1);
    }

    @Test void rejectsProviderErrors() {
        status = 503;
        assertThat(new IdentityAvailabilityService(issuer).isAvailable()).isFalse();
    }

    @Test void rejectsMissingEndpoints() {
        body = "{\"issuer\":\"" + issuer + "\"}";
        assertThat(new IdentityAvailabilityService(issuer).isAvailable()).isFalse();
    }

    @Test void rejectsOversizedResponses() {
        body = " ".repeat(129 * 1024) + body;
        assertThat(new IdentityAvailabilityService(issuer).isAvailable()).isFalse();
    }

    @Test void timesOutTheWholeProbe() {
        hang = true;
        IdentityAvailabilityService service = new IdentityAvailabilityService(issuer);
        assertTimeout(Duration.ofSeconds(4), () -> assertThat(service.isAvailable()).isFalse());
    }
}
