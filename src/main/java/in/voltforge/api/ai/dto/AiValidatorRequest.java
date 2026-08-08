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
public class AiValidatorRequest {
    private String boardType;
    private List<ComponentInfo> components;
    private List<WireInfo> wires;
    private String code;
    private String context;
    private List<String> compilerDiagnostics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentInfo {
        private String id;
        private String type;
        private String name;
        private List<Map<String, Object>> pins;
        private Map<String, Object> properties;
        private Double x;
        private Double y;
        private Double height;
        private Double width;
        private Double rotation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WireInfo {
        private String id;
        private String fromComponent;
        private String fromPin;
        private String toComponent;
        private String toPin;
        private String color;
    }
}

