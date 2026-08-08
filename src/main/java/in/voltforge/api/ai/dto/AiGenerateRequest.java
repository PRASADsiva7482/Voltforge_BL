package in.voltforge.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import in.voltforge.api.common.enums.BoardType;
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
public class AiGenerateRequest {

    private String prompt;

    private BoardType boardType;

    private List<String> componentTypes;

    private List<Map<String, Object>> components;

    private List<Map<String, Object>> wires;

    private String code;

    private String context;
}

