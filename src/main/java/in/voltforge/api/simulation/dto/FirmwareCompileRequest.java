package in.voltforge.api.simulation.dto;

import in.voltforge.api.common.enums.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirmwareCompileRequest {

    @NotBlank(message = "Source code is required")
    @Size(max = 262144, message = "Source code must be smaller than 256 KB")
    private String source;

    private BoardType boardType;

    private String sketchName;
}
