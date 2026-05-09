package in.voltforge.api.project.dto;

import in.voltforge.api.common.enums.BoardType;
import in.voltforge.api.user.dto.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSummaryResponse {
    private String id;
    private String name;
    private String description;
    private BoardType boardType;
    private Boolean isPublic;
    private Integer forkCount;
    private Integer viewCount;
    private String thumbnailUrl;
    private String tags;
    private UserResponse owner;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
