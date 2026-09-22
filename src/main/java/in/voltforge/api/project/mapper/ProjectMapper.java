package in.voltforge.api.project.mapper;

import in.voltforge.api.project.dto.CodeFileResponse;
import in.voltforge.api.project.dto.ProjectResponse;
import in.voltforge.api.project.dto.ProjectSummaryResponse;
import in.voltforge.api.project.entity.CodeFile;
import in.voltforge.api.project.entity.Project;
import in.voltforge.api.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProjectMapper {

    private final UserMapper userMapper;

    public ProjectResponse toResponse(Project project) {
        if (project == null) return null;
        return ProjectResponse.builder()
                .id(project.getId())
                .documentRevision(Long.toString(project.getDocumentRevision()))
                .name(project.getName())
                .description(project.getDescription())
                .boardType(project.getBoardType())
                .canvasLayout(project.getCanvasLayout())
                .componentConfig(project.getComponentConfig())
                .isPublic(project.getIsPublic())
                .forkCount(project.getForkCount())
                .viewCount(project.getViewCount())
                .forkedFromId(project.getForkedFrom() != null ? project.getForkedFrom().getId() : null)
                .forkedFromName(project.getForkedFrom() != null ? project.getForkedFrom().getName() : null)
                .thumbnailUrl(project.getThumbnailUrl())
                .tags(project.getTags())
                .owner(userMapper.toResponse(project.getOwner()))
                .codeFiles(toCodeFileResponseList(project.getCodeFiles()))
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    public ProjectSummaryResponse toSummaryResponse(Project project) {
        if (project == null) return null;
        return ProjectSummaryResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .boardType(project.getBoardType())
                .isPublic(project.getIsPublic())
                .forkCount(project.getForkCount())
                .viewCount(project.getViewCount())
                .thumbnailUrl(project.getThumbnailUrl())
                .tags(project.getTags())
                .owner(userMapper.toResponse(project.getOwner()))
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    public CodeFileResponse toCodeFileResponse(CodeFile codeFile) {
        if (codeFile == null) return null;
        return CodeFileResponse.builder()
                .id(codeFile.getId())
                .filename(codeFile.getFilename())
                .content(codeFile.getContent())
                .language(codeFile.getLanguage())
                .sortOrder(codeFile.getSortOrder())
                .createdAt(codeFile.getCreatedAt())
                .updatedAt(codeFile.getUpdatedAt())
                .build();
    }

    private List<CodeFileResponse> toCodeFileResponseList(List<CodeFile> codeFiles) {
        if (codeFiles == null) return Collections.emptyList();
        return codeFiles.stream()
                .map(this::toCodeFileResponse)
                .collect(Collectors.toList());
    }
}
