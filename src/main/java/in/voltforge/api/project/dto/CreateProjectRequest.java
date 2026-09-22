package in.voltforge.api.project.dto;

import in.voltforge.api.common.enums.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
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
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 200, message = "Project name must not exceed 200 characters")
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    private BoardType boardType;

    private Map<String, Object> canvasLayout;

    private Map<String, Object> componentConfig;

    private Boolean isPublic;

    private String tags;

    @Valid
    @Size(max = 50, message = "A project may contain at most 50 code files")
    private List<CodeFileRequest> codeFiles;
}
