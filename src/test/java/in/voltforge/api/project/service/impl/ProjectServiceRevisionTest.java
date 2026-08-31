package in.voltforge.api.project.service.impl;

import in.voltforge.api.project.dto.UpdateProjectRequest;
import in.voltforge.api.project.entity.Project;
import in.voltforge.api.project.mapper.ProjectMapper;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.project.repository.ProjectShareRepository;
import in.voltforge.api.user.mapper.UserMapper;
import in.voltforge.api.user.entity.User;
import in.voltforge.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceRevisionTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectShareRepository projectShareRepository;

    private final ProjectMapper projectMapper = new ProjectMapper(new UserMapper());

    private ProjectServiceImpl service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectServiceImpl(projectRepository, userRepository, projectShareRepository, projectMapper);
        User owner = User.builder().keycloakId("user-029").username("owner").build();
        owner.setId("owner-029");
        project = Project.builder().owner(owner).name("revision test").build();
        project.setId("project-029");
        project.setUpdatedAt(LocalDateTime.of(2026, 8, 31, 12, 0));
        when(projectRepository.findById("project-029")).thenReturn(Optional.of(project));
        when(userRepository.findByKeycloakId("user-029")).thenReturn(Optional.of(owner));
    }

    @Test
    void rejectsStaleEditorRevisionBeforeMutatingOrSaving() {
        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .description("must not overwrite newer work")
                .expectedRevision("2026-08-31T11:59")
                .build();

        assertThatThrownBy(() -> service.updateProject("project-029", "user-029", request))
                .isInstanceOfSatisfying(in.voltforge.api.common.exception.ConflictException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo("PROJECT_REVISION_STALE"));
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void acceptsMatchingEditorRevisionAndReturnsTheSavedProject() {
        when(projectRepository.save(project)).thenReturn(project);

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .description("same revision")
                .expectedRevision("2026-08-31T12:00")
                .build();

        service.updateProject("project-029", "user-029", request);

        verify(projectRepository).save(project);
    }
}
