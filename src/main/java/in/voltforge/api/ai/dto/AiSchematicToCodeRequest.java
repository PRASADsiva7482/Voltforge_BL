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
public class AiSchematicToCodeRequest {
    private String boardType;
    private List<ComponentInfo> components;
    private List<WireInfo> wires;
    private String additionalInstructions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentInfo {
        private String type;
        private String name;
        private Map<String, Object> properties;
        private List<String> connectedPins;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WireInfo {
        private String fromComponent;
        private String fromPin;
        private String toComponent;
        private String toPin;
    }
}
