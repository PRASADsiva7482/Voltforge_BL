package in.voltforge.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket / STOMP configuration hardened for high-concurrency collaborative
 * sessions.
 *
 * Key production settings:
 * - Heartbeats keep idle connections alive without database load
 * - Message size cap prevents OOM from malicious/corrupted large payloads
 * - Send timeout + buffer limits prevent slow-consumer back-pressure from
 *   blocking the broker thread
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * TaskScheduler required by the simple broker when heartbeat values are set.
     * Without this bean Spring throws:
     * "Heartbeat values configured but no TaskScheduler provided"
     */
    @Bean
    public TaskScheduler wsHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // ── In-memory broker with heartbeat ───────────────────────────────────
        // Server sends a heartbeat every 25 s; expects one from clients every 25 s.
        // This keeps Nginx/load-balancer connections alive and frees stale sockets.
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] { 25_000, 25_000 })
                .setTaskScheduler(wsHeartbeatScheduler());

        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                // SockJS heartbeat interval (ms) — keeps long-poll transports alive
                .setHeartbeatTime(25_000)
                // Disconnect stale SockJS sessions after 10 s of no activity
                .setDisconnectDelay(10_000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // ── Message size limits ───────────────────────────────────────────────
        // Maximum inbound message: 1 MB. A delta-sync canvas patch should never
        // approach this; large payloads indicate full-layout saves (anti-pattern).
        registration.setMessageSizeLimit(1024 * 1024); // 1 MB inbound message limit

        // ── Slow-consumer protection ──────────────────────────────────────────
        // If a client cannot drain its send buffer within 15 s, the session is
        // closed to prevent the broker thread from blocking indefinitely.
        registration.setSendTimeLimit(15 * 1000); // 15 s send deadline
        registration.setSendBufferSizeLimit(512 * 1024); // 512 KB max outbound queue
    }
}
