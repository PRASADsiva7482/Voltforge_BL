package in.voltforge.api.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@JsonIgnoreProperties(ignoreUnknown = false)
public class AiMemoryCorrectionRequest {
    @NotBlank
    @Size(max = 1200)
    private String content;

    @NotBlank
    @Size(max = 160)
    private String projectRevision;

    @Min(1)
    @Max(2160)
    private Integer expiresInHours;

    @Min(1)
    private int expectedVersion;

    @AssertTrue(message = "Memory corrections require explicit approval")
    private boolean approved;
}
