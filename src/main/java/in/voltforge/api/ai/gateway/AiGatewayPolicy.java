package in.voltforge.api.ai.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.voltforge.api.ai.dto.AiChatRequest;
import in.voltforge.api.config.VoltforgeAiConfig;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Validates the untrusted browser payload before it is sent to the local AI.
 * The project and revision supplied by the authenticated gateway are the
 * authoritative scope; nested copied context must agree with them.
 */
@Component
public class AiGatewayPolicy {

    private final ObjectMapper objectMapper;
    private final VoltforgeAiConfig config;

    public AiGatewayPolicy(ObjectMapper objectMapper, VoltforgeAiConfig config) {
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public void validateChatRequest(AiChatRequest request) {
        if (request == null) {
            throw invalid("AI_REQUEST_INVALID", "AI request is required");
        }
        if (request.getAuthenticatedUserId() == null || request.getAuthenticatedUserId().isBlank()) {
            throw new AiGatewayException(HttpStatus.UNAUTHORIZED,
                    "AI_AUTHENTICATION_REQUIRED", "Authenticated AI identity required", false);
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw invalid("AI_REQUEST_INVALID", "AI message is required");
        }
        if (request.getProjectId() != null && request.getProjectId().isBlank()) {
            throw invalid("AI_PROJECT_SCOPE_INVALID", "Project scope is invalid");
        }
        if (request.getProjectRevision() != null && !request.getProjectRevision().isBlank()
                && (request.getProjectId() == null || request.getProjectId().isBlank())) {
            throw invalid("AI_PROJECT_SCOPE_INVALID", "A project revision requires a project scope");
        }
        if (request.getHistory() != null && request.getHistory().size() > 20) {
            throw invalid("AI_REQUEST_LIMIT_REACHED", "Conversation history is too large");
        }
        if (request.getContext() != null && request.getContext().length() > 60_000
                || request.getCanvasContext() != null && request.getCanvasContext().length() > 60_000
                || request.getCode() != null && request.getCode().length() > 200_000) {
            throw invalid("AI_REQUEST_LIMIT_REACHED", "AI context is too large");
        }

        final int requestBytes;
        try {
            requestBytes = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception ex) {
            throw invalid("AI_REQUEST_INVALID", "AI request could not be validated");
        }
        if (requestBytes > config.getMaxRequestBytes()) {
            throw invalid("AI_REQUEST_LIMIT_REACHED", "AI request exceeds the permitted size");
        }

        String projectId = request.getProjectId();
        String projectRevision = request.getProjectRevision();
        inspectStructuredContext(request.getComponents(), "components", projectId, projectRevision);
        inspectStructuredContext(request.getWires(), "wires", projectId, projectRevision);
        inspectStructuredContext(request.getNetlist(), "netlist", projectId, projectRevision);
        inspectStructuredContext(request.getCanvasData(), "canvasData", projectId, projectRevision);
        inspectStructuredContext(request.getSimulationState(), "simulationState", projectId, projectRevision);
        inspectStructuredContext(request.getFiles(), "files", projectId, projectRevision);
        inspectStructuredContext(request.getDiagnostics(), "diagnostics", projectId, projectRevision);
        inspectStructuredContext(request.getRetrievedEvidence(), "retrievedEvidence", projectId, projectRevision);
        inspectJsonContext(request.getContext(), "context", projectId, projectRevision);
        inspectJsonContext(request.getCanvasContext(), "canvasContext", projectId, projectRevision);
    }

    private void inspectStructuredContext(Object value, String field, String projectId, String projectRevision) {
        if (value == null) {
            return;
        }
        inspectNode(objectMapper.valueToTree(value), field, projectId, projectRevision);
    }

    private void inspectJsonContext(String value, String field, String projectId, String projectRevision) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return;
        }
        try {
            inspectNode(objectMapper.readTree(trimmed), field, projectId, projectRevision);
        } catch (JsonProcessingException ignored) {
            // Free-form context is intentionally left to the AI context
            // compiler. Only an explicitly structured context is scope-checked.
        }
    }

    private void inspectNode(JsonNode node, String field, String projectId, String projectRevision) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if ("projectId".equalsIgnoreCase(key) || "project_id".equalsIgnoreCase(key)) {
                    if (projectId == null || !value.isTextual() || !projectId.equals(value.asText())) {
                        throw invalid("AI_PROJECT_CONTEXT_MISMATCH", "AI context does not match the selected project");
                    }
                }
                if ("projectRevision".equalsIgnoreCase(key) || "project_revision".equalsIgnoreCase(key)) {
                    if (projectRevision == null || !value.isTextual() || !projectRevision.equals(value.asText())) {
                        throw invalid("AI_PROJECT_REVISION_MISMATCH", "AI context does not match the selected project revision");
                    }
                }
                inspectNode(value, field, projectId, projectRevision);
            });
        } else if (node.isArray()) {
            node.forEach(item -> inspectNode(item, field, projectId, projectRevision));
        }
    }

    private AiGatewayException invalid(String code, String message) {
        return new AiGatewayException(HttpStatus.BAD_REQUEST, code, message, false);
    }
}
