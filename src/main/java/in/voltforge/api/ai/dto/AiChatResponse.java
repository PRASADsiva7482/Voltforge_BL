package in.voltforge.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class AiChatResponse {
    private String reply;
    private String generatedCode;
    private boolean hasCode;
    private Double confidence;
    private List<Map<String, String>> citations;
    private List<AiWireSuggestion> wireSuggestions;
    private List<Map<String, Object>> additions;
    private List<Map<String, Object>> removals;
    private List<Map<String, Object>> valueChanges;
    private List<Map<String, Object>> codeFixes;
}
