package in.voltforge.api.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWireSuggestion {
    private String fromComponentId;
    private String fromPin;
    private String toComponentId;
    private String toPin;
    private String color;
    private String description;
}
