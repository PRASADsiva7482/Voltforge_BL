package in.voltforge.api.project.service;

import in.voltforge.api.common.dto.PagedResponse;
import in.voltforge.api.project.dto.CreateProjectRequest;
import in.voltforge.api.project.dto.ProjectResponse;
import in.voltforge.api.project.dto.ProjectSummaryResponse;
import in.voltforge.api.project.dto.UpdateProjectRequest;

import java.util.Map;

public interface ProjectService {

    ProjectResponse createProject(String keycloakId, CreateProjectRequest request);

    ProjectResponse getProject(String projectId, String keycloakId);

    boolean canAccessProject(String projectId, String keycloakId);

    /** Returns the current optimistic-concurrency revision for an accessible project. */
    String getProjectRevision(String projectId, String keycloakId);

    boolean canEditProject(String projectId, String keycloakId);

    void scheduleCanvasLayoutSave(String projectId, String keycloakId,
                                  Map<String, Object> canvasLayout);

    ProjectResponse updateProject(String projectId, String keycloakId, UpdateProjectRequest request);

    void deleteProject(String projectId, String keycloakId);

    ProjectResponse forkProject(String projectId, String keycloakId);

    PagedResponse<ProjectSummaryResponse> getUserProjects(String keycloakId, int page, int size);

    PagedResponse<ProjectSummaryResponse> getPublicProjects(int page, int size);

    PagedResponse<ProjectSummaryResponse> searchPublicProjects(String query, int page, int size);

    PagedResponse<ProjectSummaryResponse> getTemplates(int page, int size);
}
