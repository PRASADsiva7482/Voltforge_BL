package in.voltforge.api.project.service;

import in.voltforge.api.common.dto.PagedResponse;
import in.voltforge.api.project.dto.CreateProjectRequest;
import in.voltforge.api.project.dto.ProjectResponse;
import in.voltforge.api.project.dto.ProjectSummaryResponse;
import in.voltforge.api.project.dto.UpdateProjectRequest;


public interface ProjectService {

    ProjectResponse createProject(String keycloakId, CreateProjectRequest request);

    ProjectResponse getProject(String projectId, String keycloakId);

    boolean canAccessProject(String projectId, String keycloakId);

    /** Legacy timestamp fingerprint for context validation; editor saves use ProjectResponse.documentRevision. */
    String getProjectRevision(String projectId, String keycloakId);

    boolean canEditProject(String projectId, String keycloakId);

    ProjectResponse updateProject(String projectId, String keycloakId, UpdateProjectRequest request);

    void deleteProject(String projectId, String keycloakId);

    ProjectResponse forkProject(String projectId, String keycloakId);

    PagedResponse<ProjectSummaryResponse> getUserProjects(String keycloakId, int page, int size);

    PagedResponse<ProjectSummaryResponse> getPublicProjects(int page, int size);

    PagedResponse<ProjectSummaryResponse> searchPublicProjects(String query, int page, int size);

    PagedResponse<ProjectSummaryResponse> getTemplates(int page, int size);
}
