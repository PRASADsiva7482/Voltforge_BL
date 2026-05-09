package in.voltforge.api.project.dto;

import jakarta.validation.constraints.NotBlank;
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
    private String filename;

    private String content;

    private String language;

    private Integer sortOrder;
}
