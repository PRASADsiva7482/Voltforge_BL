package in.voltforge.api.ai.dto;

import in.voltforge.api.common.enums.BoardType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateRequest {

    @NotBlank(message = "Prompt is required")
    private String prompt;

    private BoardType boardType;

    private List<String> componentTypes;
}
