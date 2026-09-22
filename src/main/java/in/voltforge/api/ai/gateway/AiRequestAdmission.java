package in.voltforge.api.ai.gateway;

import in.voltforge.api.ai.observability.AiTelemetry;
import in.voltforge.api.config.VoltforgeAiConfig;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Small process-local admission controller for long-lived AI streams.
 * Admission is non-blocking: a busy user receives a typed retryable error and
 * normal project editing threads are never held waiting for AI capacity.
 */
@Component
public class AiRequestAdmission {

    private final VoltforgeAiConfig config;
    private final AiTelemetry telemetry;
    private final Map<String, Integer> activeByUser = new HashMap<>();
    private final Map<String, Integer> activeByProject = new HashMap<>();

    public AiRequestAdmission(VoltforgeAiConfig config) {
        this(config, new AiTelemetry());
    }

    @Autowired
    public AiRequestAdmission(VoltforgeAiConfig config, AiTelemetry telemetry) {
        this.config = config;
        this.telemetry = telemetry;
    }

    public synchronized Lease acquireStreaming(String userId, String projectId) {
        if (userId == null || userId.isBlank()) {
            telemetry.recordAdmissionRejected("AI_AUTHENTICATION_REQUIRED");
            throw new AiGatewayException(HttpStatus.UNAUTHORIZED,
                    "AI_AUTHENTICATION_REQUIRED", "Authenticated AI identity required", false);
        }
        String projectKey = projectId == null || projectId.isBlank() ? null : projectId;
        int userActive = activeByUser.getOrDefault(userId, 0);
        if (userActive >= config.getMaxConcurrentStreamsPerUser()) {
            telemetry.recordAdmissionRejected("AI_USER_STREAM_LIMIT");
            throw new AiGatewayException(HttpStatus.TOO_MANY_REQUESTS,
                    "AI_USER_STREAM_LIMIT", "Too many AI responses are already active for this user", true);
        }
        if (projectKey != null && activeByProject.getOrDefault(projectKey, 0)
                >= config.getMaxConcurrentStreamsPerProject()) {
            telemetry.recordAdmissionRejected("AI_PROJECT_STREAM_LIMIT");
            throw new AiGatewayException(HttpStatus.TOO_MANY_REQUESTS,
                    "AI_PROJECT_STREAM_LIMIT", "Too many AI responses are already active for this project", true);
        }
        activeByUser.merge(userId, 1, Integer::sum);
        if (projectKey != null) {
            activeByProject.merge(projectKey, 1, Integer::sum);
        }
        return new Lease(userId, projectKey);
    }

    public synchronized int activeStreamsForUser(String userId) {
        return activeByUser.getOrDefault(userId, 0);
    }

    public final class Lease implements AutoCloseable {
        private final String userId;
        private final String projectId;
        private boolean closed;

        private Lease(String userId, String projectId) {
            this.userId = userId;
            this.projectId = projectId;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            release(userId, projectId);
        }
    }

    private synchronized void release(String userId, String projectId) {
        decrement(activeByUser, userId);
        if (projectId != null) {
            decrement(activeByProject, projectId);
        }
    }

    private void decrement(Map<String, Integer> counts, String key) {
        Integer count = counts.get(key);
        if (count == null || count <= 1) {
            counts.remove(key);
        } else {
            counts.put(key, count - 1);
        }
    }
}
