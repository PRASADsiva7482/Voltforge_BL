package in.voltforge.api.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanvasEvent {
    private String projectId;
    private String userId;
    private String eventType; // NODE_MOVED, WIRE_ADDED, COMPONENT_ADDED, NODE_REMOVED
    private Map<String, Object> payload;
    private Long timestamp;
}
