package in.voltforge.api.ai.dto;

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
public class AiGenerateResponse {
    private String status;
    private String message;
    private Map<String, Object> canvasLayout;
    private Map<String, Object> componentConfig;
    private List<AiWireSuggestion> wireSuggestions;
    private String generatedCode;
    private List<Map<String, Object>> additions;
    private List<Map<String, Object>> removals;
    private List<Map<String, Object>> codeFixes;
    private List<Map<String, String>> citations;
    private Double confidence;
}
