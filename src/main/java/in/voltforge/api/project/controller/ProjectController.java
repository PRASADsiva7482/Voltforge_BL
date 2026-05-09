package in.voltforge.api.project.controller;

import in.voltforge.api.common.dto.ApiResponse;
import in.voltforge.api.common.dto.PagedResponse;
import in.voltforge.api.project.dto.CreateProjectRequest;
import in.voltforge.api.project.dto.ProjectResponse;
import in.voltforge.api.project.dto.ProjectSummaryResponse;
import in.voltforge.api.project.dto.UpdateProjectRequest;
import in.voltforge.api.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project management APIs")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @Operation(summary = "Create a new project")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.createProject(jwt.getSubject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created successfully", project));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt != null ? jwt.getSubject() : null;
        ProjectResponse project = projectService.getProject(projectId, keycloakId);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update a project")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProjectRequest request) {
        ProjectResponse project = projectService.updateProject(projectId, jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Project updated successfully", project));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Delete a project")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt) {
        projectService.deleteProject(projectId, jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Project deleted successfully"));
    }

    @PostMapping("/{projectId}/fork")
    @Operation(summary = "Fork a public project")
    public ResponseEntity<ApiResponse<ProjectResponse>> forkProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal Jwt jwt) {
        ProjectResponse forked = projectService.forkProject(projectId, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project forked successfully", forked));
    }

    @GetMapping
    @Operation(summary = "List current user's projects")
    public ResponseEntity<ApiResponse<PagedResponse<ProjectSummaryResponse>>> getUserProjects(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<ProjectSummaryResponse> projects = projectService.getUserProjects(jwt.getSubject(), page, size);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/public")
    @Operation(summary = "Browse public projects")
    public ResponseEntity<ApiResponse<PagedResponse<ProjectSummaryResponse>>> getPublicProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<ProjectSummaryResponse> projects = projectService.getPublicProjects(page, size);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/public/search")
    @Operation(summary = "Search public projects")
    public ResponseEntity<ApiResponse<PagedResponse<ProjectSummaryResponse>>> searchPublicProjects(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PagedResponse<ProjectSummaryResponse> projects = projectService.searchPublicProjects(query, page, size);
        return ResponseEntity.ok(ApiResponse.success(projects));
    }
}
