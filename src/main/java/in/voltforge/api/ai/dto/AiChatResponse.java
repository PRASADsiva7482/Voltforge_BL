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
    private Integer schemaVersion;
    private String contractVersion;
    private String requestId;
    private String sessionId;
    private String projectRevision;
    private String model;
    private String mode;
    private Map<String, Object> artifact;
    private Map<String, Object> readiness;
    private String reply;
    private String generatedCode;
    private boolean hasCode;
    private Double confidence;
    private List<Map<String, Object>> citations;
    private List<AiWireSuggestion> wireSuggestions;
    private List<Map<String, Object>> additions;
    private List<Map<String, Object>> removals;
    private List<Map<String, Object>> valueChanges;
    private List<Map<String, Object>> codeFixes;
    private Boolean engineeringAuthorityActive;
    private Map<String, Object> engineeringAuthority;
    private List<Map<String, Object>> engineeringFindings;
    private Integer omittedEngineeringFindingCount;
    private Map<String, Object> localRetrieval;
    private Map<String, Object> internetRetrieval;
    private Map<String, Object> grounding;
    private Map<String, Object> memory;
}
