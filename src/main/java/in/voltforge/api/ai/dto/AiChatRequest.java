package in.voltforge.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatRequest {

    /** Set only by the authenticated controller; never accepted from JSON. */
    @JsonIgnore
    private String authenticatedUserId;

    @NotBlank(message = "Message is required")
    @Size(max = 6000, message = "Message must not exceed 6000 characters")
    private String message;

    @Size(max = 80)
    private String sessionId;

    @Size(max = 80)
    private String projectId;

    @Size(max = 160)
    private String projectRevision;

    private String context; // optional context (current code, canvas state, etc.)

    private String canvasContext;

    private String boardType;

    private List<Map<String, Object>> components;

    private List<Map<String, Object>> wires;

    private Map<String, Object> netlist;

    private String code;

    private Map<String, Object> canvasData;

    private Map<String, Object> simulationState;

    private List<ChatMessage> history;

    /** Bounded firmware context; the Python service performs the final size validation. */
    @Size(max = 20)
    private List<Map<String, Object>> files;

    /** Optional typed inputs consumed by the bounded context compiler. */
    @Size(max = 100)
    private List<Map<String, Object>> diagnostics;

    /** Client values are not trusted or forwarded; memory is loaded by authenticated scope. */
    @Size(max = 50)
    private List<Map<String, Object>> memory;

    /** Reserved for bounded retrieval records; client data remains untrusted. */
    @Size(max = 50)
    private List<Map<String, Object>> retrievedEvidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatMessage {
        private String role; // "user" or "assistant"
        private String content;
    }
}
