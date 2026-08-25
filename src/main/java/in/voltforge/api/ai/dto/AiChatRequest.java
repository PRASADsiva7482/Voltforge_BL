package in.voltforge.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "Message is required")
    private String message;

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
