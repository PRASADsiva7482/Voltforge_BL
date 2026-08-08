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
public class AiValidatorResponse {
    private boolean isValid;
    private int safetyScore;
    private List<ValidationIssue> issues;
    private String generalFeedback;
    private List<Map<String, Object>> additions;
    private List<Map<String, Object>> removals;
    private List<AiWireSuggestion> wireSuggestions;
    private List<Map<String, Object>> codeFixes;
    private Double confidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValidationIssue {
        private String severity; // CRITICAL, WARNING, INFO
        private String componentId;
        private String message;
        private String suggestedFix;
    }
}

