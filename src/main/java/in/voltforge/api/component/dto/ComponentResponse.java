package in.voltforge.api.component.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentResponse {
    private String id;
    private String name;
    private String category;
    private String type;
    private String description;
    private Map<String, Object> defaultProperties;
    private Map<String, Object> pinConfig;
    private String iconUrl;
    private String svgData;
    private Boolean isPremium;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
