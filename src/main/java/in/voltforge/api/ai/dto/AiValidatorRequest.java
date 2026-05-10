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
public class AiValidatorRequest {
    private String boardType;
    private List<ComponentInfo> components;
    private List<WireInfo> wires;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentInfo {
        private String id;
        private String type;
        private String name;
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
