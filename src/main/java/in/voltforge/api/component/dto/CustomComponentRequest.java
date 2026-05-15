package in.voltforge.api.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CustomComponentRequest {
    @NotBlank
    private String name;

    private String description;
    private String category = "SENSOR";
    private String type;

    @NotBlank
    private String svgData;

    private Integer width = 120;
    private Integer height = 90;

    @NotEmpty
    private List<Map<String, Object>> pins;

    private Boolean publishToCommunity = false;
}
