package in.voltforge.api.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CustomComponentRequest {
    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 2_000)
    private String description;
    private String category = "SENSOR";
    private String type;

    @NotBlank
    @Size(max = 100_000)
    private String svgData;

    @Min(20)
    @Max(1_000)
    private Integer width = 120;
    @Min(20)
    @Max(1_000)
    private Integer height = 90;

    @NotEmpty
    @Size(max = 100)
    private List<Map<String, Object>> pins;

    private Boolean publishToCommunity = false;
}
