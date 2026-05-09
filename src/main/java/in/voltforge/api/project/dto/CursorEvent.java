package in.voltforge.api.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorEvent {
    private String projectId;
    private String userId;
    private String displayName;
    private String color;
    private double x;
    private double y;
}
