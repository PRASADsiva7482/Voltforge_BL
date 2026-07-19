package in.voltforge.api.project.dto;

import in.voltforge.api.common.enums.BoardType;
import in.voltforge.api.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private String id;
    private String name;
    private String description;
    private BoardType boardType;
    private Map<String, Object> canvasLayout;
    private Map<String, Object> componentConfig;
    private Boolean isPublic;
    private Integer forkCount;
    private Integer viewCount;
    private String forkedFromId;
    private String forkedFromName;
    private String userForkId;
    private String thumbnailUrl;
    private String tags;
    private UserResponse owner;
    private List<CodeFileResponse> codeFiles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
