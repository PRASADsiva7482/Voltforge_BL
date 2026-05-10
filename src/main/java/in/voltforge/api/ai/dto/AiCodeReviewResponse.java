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
public class AiCodeReviewResponse {
    private String summary;
    private List<ReviewIssue> issues;
    private List<String> suggestions;
    private String improvedCode;
    private int score; // 0-100

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewIssue {
        private String severity; // ERROR, WARNING, INFO
        private int line;
        private String message;
        private String fix;
    }
}
