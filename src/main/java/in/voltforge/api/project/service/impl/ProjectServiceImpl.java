package in.voltforge.api.project.service.impl;

import in.voltforge.api.common.dto.PagedResponse;
import in.voltforge.api.common.enums.BoardType;
import in.voltforge.api.common.exception.ConflictException;
import in.voltforge.api.common.exception.ForbiddenException;
import in.voltforge.api.common.exception.ResourceNotFoundException;
import in.voltforge.api.project.dto.*;
import in.voltforge.api.project.entity.CodeFile;
import in.voltforge.api.project.entity.Project;
import in.voltforge.api.project.mapper.ProjectMapper;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.project.repository.ProjectShareRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectShareRepository projectShareRepository;
    private final ProjectMapper projectMapper;

    // ── Debounce infrastructure for canvas-layout saves ───────────────────────
    // One scheduler thread is sufficient; all tasks are lightweight DB writes.
    private final ScheduledExecutorService debounceScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "canvas-save-debounce");
                t.setDaemon(true);
                return t;
            });

    /**
     * Pending futures keyed by projectId. A new event cancels the previous
     * pending flush and schedules a fresh one, so only the final canvas state
     * after DEBOUNCE_DELAY_MS of inactivity is written to MySQL.
     */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingCanvasSaves =
            new ConcurrentHashMap<>();

    /** Idle window after the last canvas event before committing to DB (ms). */
    private static final long DEBOUNCE_DELAY_MS = 2_000;

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

        if (!canAccessProject(projectId, keycloakId)) {
            throw new ForbiddenException("Access denied to project");
        }

        // Count only successful reads. Previously unauthorized requests could
        // increment the counter before the access check.
        projectRepository.incrementViewCount(projectId);

        ProjectResponse response = projectMapper.toResponse(project);

        // Populate userForkId if the user has already forked this project
        if (keycloakId != null) {
            User user = userRepository.findByKeycloakId(keycloakId).orElse(null);
            if (user != null) {
                List<Project> forks = projectRepository.findByOwnerIdAndForkedFromId(user.getId(), projectId);
                if (!forks.isEmpty()) {
                    response.setUserForkId(forks.get(0).getId());
                }
            }
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessProject(String projectId, String keycloakId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return false;
        }
        if (Boolean.TRUE.equals(project.getIsPublic())) {
            return true;
        }
        if (keycloakId == null || keycloakId.isBlank()) {
            return false;
        }

        User user = userRepository.findByKeycloakId(keycloakId).orElse(null);
        return user != null && (project.getOwner().getId().equals(user.getId())
                || projectShareRepository.existsByProjectIdAndSharedWithUserId(projectId, user.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public String getProjectRevision(String projectId, String keycloakId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        if (!canAccessProject(projectId, keycloakId)) {
            throw new ForbiddenException("Access denied to project");
        }
        return project.getUpdatedAt() != null ? project.getUpdatedAt().toString() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canEditProject(String projectId, String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
            return false;
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        User user = userRepository.findByKeycloakId(keycloakId).orElse(null);
        if (project == null || user == null) {
            return false;
        }
        if (project.getOwner().getId().equals(user.getId())) {
            return true;
        }
        return projectShareRepository.findByProjectIdAndSharedWithUserId(projectId, user.getId())
                .map(share -> share.getPermission() != null
                        && (share.getPermission().name().equals("EDIT")
                        || share.getPermission().name().equals("ADMIN")))
                .orElse(false);
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

        if (request.getExpectedRevision() != null
                && !request.getExpectedRevision().isBlank()
                && (project.getUpdatedAt() == null
                || !request.getExpectedRevision().equals(project.getUpdatedAt().toString()))) {
            throw new ConflictException(
                    "PROJECT_REVISION_STALE",
                    "The project changed since this editor state was loaded. Reload it before saving.");
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

    /**
     * Debounced canvas-layout persist — safe to call on every WebSocket canvas-delta event.
     *
     * The actual DB write is deferred until DEBOUNCE_DELAY_MS after the last call
     * for a given project, collapsing N rapid WS events into a single MySQL UPDATE.
     *
     * This method is intentionally NOT @Transactional at the call site — the inner
     * Runnable opens its own short transaction via the repository.
     *
     * @param projectId   ID of the project to persist
     * @param keycloakId  Caller's Keycloak subject (ownership guard)
     * @param canvasLayout The latest canvas state (nodes + wires JSON)
     */
    public void scheduleCanvasLayoutSave(String projectId, String keycloakId,
                                         Map<String, Object> canvasLayout) {
        // Cancel any pending flush for this project
        ScheduledFuture<?> existing = pendingCanvasSaves.get(projectId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
        }

        // Schedule a new flush after the debounce window
        ScheduledFuture<?> future = debounceScheduler.schedule(() -> {
            try {
                flushCanvasLayoutToDb(projectId, keycloakId, canvasLayout);
            } finally {
                pendingCanvasSaves.remove(projectId);
            }
        }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);

        pendingCanvasSaves.put(projectId, future);
        log.debug("Canvas save debounced for project '{}' — flushing in {} ms", projectId, DEBOUNCE_DELAY_MS);
    }

    /**
     * Performs the actual DB write inside a dedicated transaction.
     * Called only by the debounce scheduler, never directly from WS events.
     */
    @Transactional
    protected void flushCanvasLayoutToDb(String projectId, String keycloakId,
                                          Map<String, Object> canvasLayout) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            log.warn("Canvas flush skipped — project '{}' not found", projectId);
            return;
        }
        // Re-check edit permission in the delayed task because the original
        // WebSocket request has already returned by the time this runs.
        if (!canEditProject(projectId, keycloakId)) {
            log.warn("Canvas flush rejected — user '{}' cannot edit project '{}'",
                     keycloakId, projectId);
            return;
        }
        Map<String, Object> persistedCanvasLayout = new LinkedHashMap<>(canvasLayout);
        Object pcbLayout = persistedCanvasLayout.remove("pcbLayout");
        project.setCanvasLayout(persistedCanvasLayout);
        if (pcbLayout != null) {
            Map<String, Object> componentConfig = project.getComponentConfig() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(project.getComponentConfig());
            componentConfig.put("pcbLayout", pcbLayout);
            project.setComponentConfig(componentConfig);
        }
        projectRepository.save(project);
        log.info("Canvas layout flushed to DB for project '{}'", projectId);
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
