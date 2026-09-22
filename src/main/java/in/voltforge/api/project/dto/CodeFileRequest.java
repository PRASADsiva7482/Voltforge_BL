package in.voltforge.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeFileRequest {

    @NotBlank(message = "Filename is required")
    @Size(max = 120, message = "Filename must be at most 120 characters")
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$", message = "Filename may only contain letters, numbers, dots, underscores, and hyphens")
    private String filename;

    @Size(max = 1_000_000, message = "Code file is too large")
    private String content;

    private String language;

    private Integer sortOrder;
}
