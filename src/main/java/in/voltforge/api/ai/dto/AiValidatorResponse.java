package in.voltforge.api.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiValidatorResponse {
    private boolean isValid;
    private int safetyScore;
    private List<ValidationIssue> issues;
    private String generalFeedback;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationIssue {
        private String severity; // CRITICAL, WARNING, INFO
        private String componentId;
        private String message;
        private String suggestedFix;
    }
}
