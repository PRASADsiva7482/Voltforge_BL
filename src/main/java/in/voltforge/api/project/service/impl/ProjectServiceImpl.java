package in.voltforge.api.project.service.impl;

import in.voltforge.api.common.dto.PagedResponse;
import in.voltforge.api.common.enums.BoardType;
import in.voltforge.api.common.exception.ForbiddenException;
import in.voltforge.api.common.exception.ResourceNotFoundException;
import in.voltforge.api.project.dto.*;
import in.voltforge.api.project.entity.CodeFile;
import in.voltforge.api.project.entity.Project;
import in.voltforge.api.project.mapper.ProjectMapper;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.project.service.ProjectService;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;

    @Override
    @Transactional
    public ProjectResponse createProject(String keycloakId, CreateProjectRequest request) {
        User owner = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));

        Project project = Project.builder()
                .owner(owner)
                .name(request.getName())
                .description(request.getDescription())
                .boardType(request.getBoardType() != null ? request.getBoardType() : BoardType.ARDUINO_UNO)
                .canvasLayout(request.getCanvasLayout())
                .componentConfig(request.getComponentConfig())
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : false)
                .tags(request.getTags())
                .codeFiles(new ArrayList<>())
                .shares(new ArrayList<>())
                .build();

        // Add code files
        if (request.getCodeFiles() != null && !request.getCodeFiles().isEmpty()) {
            for (CodeFileRequest cfReq : request.getCodeFiles()) {
                CodeFile codeFile = CodeFile.builder()
                        .filename(cfReq.getFilename())
                        .content(cfReq.getContent())
                        .language(cfReq.getLanguage() != null ? cfReq.getLanguage() : "cpp")
                        .sortOrder(cfReq.getSortOrder() != null ? cfReq.getSortOrder() : 0)
                        .build();
                project.addCodeFile(codeFile);
            }
        } else {
            // Default sketch file
            CodeFile defaultSketch = CodeFile.builder()
                    .filename("sketch.ino")
                    .content("void setup() {\n  // Initialize here\n}\n\nvoid loop() {\n  // Main loop\n}\n")
                    .language("cpp")
                    .sortOrder(0)
                    .build();
            project.addCodeFile(defaultSketch);
        }

        project = projectRepository.save(project);
        log.info("Created project '{}' for user '{}'", project.getName(), owner.getUsername());
        return projectMapper.toResponse(project);
    }

    @Override
    @Transactional
    public ProjectResponse getProject(String projectId, String keycloakId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // Increment view count
        projectRepository.incrementViewCount(projectId);

        // Check access: public projects are accessible to anyone
        if (!project.getIsPublic()) {
            if (keycloakId == null) {
                throw new ForbiddenException("Access denied to private project");
            }
            User user = userRepository.findByKeycloakId(keycloakId).orElse(null);
            if (user == null || !project.getOwner().getId().equals(user.getId())) {
                throw new ForbiddenException("Access denied to private project");
            }
        }

        return projectMapper.toResponse(project);
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(String projectId, String keycloakId, UpdateProjectRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));

        if (!project.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenException("You are not the owner of this project");
        }

        if (request.getName() != null) project.setName(request.getName());
        if (request.getDescription() != null) project.setDescription(request.getDescription());
        if (request.getBoardType() != null) project.setBoardType(request.getBoardType());
        if (request.getCanvasLayout() != null) project.setCanvasLayout(request.getCanvasLayout());
        if (request.getComponentConfig() != null) project.setComponentConfig(request.getComponentConfig());
        if (request.getIsPublic() != null) project.setIsPublic(request.getIsPublic());
        if (request.getTags() != null) project.setTags(request.getTags());

        // Update code files
        if (request.getCodeFiles() != null) {
            java.util.Map<String, CodeFileRequest> requestedFiles = request.getCodeFiles().stream()
                    .collect(java.util.stream.Collectors.toMap(CodeFileRequest::getFilename, f -> f));

            // Remove files not in the request
            project.getCodeFiles().removeIf(cf -> !requestedFiles.containsKey(cf.getFilename()));

            // Update existing and add new
            for (CodeFileRequest cfReq : request.getCodeFiles()) {
                java.util.Optional<CodeFile> existingOpt = project.getCodeFiles().stream()
                        .filter(cf -> cf.getFilename().equals(cfReq.getFilename()))
                        .findFirst();

                if (existingOpt.isPresent()) {
                    CodeFile existing = existingOpt.get();
                    existing.setContent(cfReq.getContent());
                    existing.setLanguage(cfReq.getLanguage() != null ? cfReq.getLanguage() : "cpp");
                    existing.setSortOrder(cfReq.getSortOrder() != null ? cfReq.getSortOrder() : 0);
                } else {
                    CodeFile codeFile = CodeFile.builder()
                            .filename(cfReq.getFilename())
                            .content(cfReq.getContent())
                            .language(cfReq.getLanguage() != null ? cfReq.getLanguage() : "cpp")
                            .sortOrder(cfReq.getSortOrder() != null ? cfReq.getSortOrder() : 0)
                            .build();
                    project.addCodeFile(codeFile);
                }
            }
        }

        project = projectRepository.save(project);
        log.info("Updated project '{}' by user '{}'", project.getName(), user.getUsername());
        return projectMapper.toResponse(project);
    }

    @Override
    @Transactional
    public void deleteProject(String projectId, String keycloakId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));

        if (!project.getOwner().getId().equals(user.getId())) {
            throw new ForbiddenException("You are not the owner of this project");
        }

        projectRepository.delete(project);
        log.info("Deleted project '{}' by user '{}'", project.getName(), user.getUsername());
    }

    @Override
    @Transactional
    public ProjectResponse forkProject(String projectId, String keycloakId) {
        Project original = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (!original.getIsPublic()) {
            throw new ForbiddenException("Cannot fork a private project");
        }

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));

        Project forked = Project.builder()
                .owner(user)
                .name(original.getName() + " (Fork)")
                .description(original.getDescription())
                .boardType(original.getBoardType())
                .canvasLayout(original.getCanvasLayout())
                .componentConfig(original.getComponentConfig())
                .isPublic(false)
                .forkedFrom(original)
                .tags(original.getTags())
                .codeFiles(new ArrayList<>())
                .shares(new ArrayList<>())
                .build();

        // Copy code files
        for (CodeFile originalFile : original.getCodeFiles()) {
            CodeFile copiedFile = CodeFile.builder()
                    .filename(originalFile.getFilename())
                    .content(originalFile.getContent())
                    .language(originalFile.getLanguage())
                    .sortOrder(originalFile.getSortOrder())
                    .build();
            forked.addCodeFile(copiedFile);
        }

        forked = projectRepository.save(forked);

        // Increment fork count on original
        projectRepository.incrementForkCount(projectId);

        log.info("User '{}' forked project '{}'", user.getUsername(), original.getName());
        return projectMapper.toResponse(forked);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProjectSummaryResponse> getUserProjects(String keycloakId, int page, int size) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "keycloakId", keycloakId));

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Project> projectPage = projectRepository.findByOwnerId(user.getId(), pageRequest);

        List<ProjectSummaryResponse> content = projectPage.getContent().stream()
                .map(projectMapper::toSummaryResponse)
                .collect(Collectors.toList());

        return PagedResponse.<ProjectSummaryResponse>builder()
                .content(content)
                .page(projectPage.getNumber())
                .size(projectPage.getSize())
                .totalElements(projectPage.getTotalElements())
                .totalPages(projectPage.getTotalPages())
                .first(projectPage.isFirst())
                .last(projectPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProjectSummaryResponse> getPublicProjects(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> projectPage = projectRepository.findByIsPublicTrue(pageRequest);

        List<ProjectSummaryResponse> content = projectPage.getContent().stream()
                .map(projectMapper::toSummaryResponse)
                .collect(Collectors.toList());

        return PagedResponse.<ProjectSummaryResponse>builder()
                .content(content)
                .page(projectPage.getNumber())
                .size(projectPage.getSize())
                .totalElements(projectPage.getTotalElements())
                .totalPages(projectPage.getTotalPages())
                .first(projectPage.isFirst())
                .last(projectPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProjectSummaryResponse> searchPublicProjects(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> projectPage = projectRepository.searchPublicProjects(query, pageRequest);

        List<ProjectSummaryResponse> content = projectPage.getContent().stream()
                .map(projectMapper::toSummaryResponse)
                .collect(Collectors.toList());

        return PagedResponse.<ProjectSummaryResponse>builder()
                .content(content)
                .page(projectPage.getNumber())
                .size(projectPage.getSize())
                .totalElements(projectPage.getTotalElements())
                .totalPages(projectPage.getTotalPages())
                .first(projectPage.isFirst())
                .last(projectPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProjectSummaryResponse> getTemplates(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "forkCount"));
        Page<Project> projectPage = projectRepository.findTemplates(pageRequest);

        List<ProjectSummaryResponse> content = projectPage.getContent().stream()
                .map(projectMapper::toSummaryResponse)
                .collect(Collectors.toList());

        return PagedResponse.<ProjectSummaryResponse>builder()
                .content(content)
                .page(projectPage.getNumber())
                .size(projectPage.getSize())
                .totalElements(projectPage.getTotalElements())
                .totalPages(projectPage.getTotalPages())
                .first(projectPage.isFirst())
                .last(projectPage.isLast())
                .build();
    }
}
