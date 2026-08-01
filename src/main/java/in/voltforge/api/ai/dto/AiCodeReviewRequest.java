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
public class AiCodeReviewRequest {
    private String code;
    private String boardType;
    private List<String> componentTypes;
    private List<Map<String, Object>> components;
    private List<Map<String, Object>> wires;
    private String circuitDescription;
    private List<String> compilerDiagnostics;
}
