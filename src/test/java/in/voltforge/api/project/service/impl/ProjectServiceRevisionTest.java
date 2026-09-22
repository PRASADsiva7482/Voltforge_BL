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
import static org.assertj.core.api.Assertions.assertThat;
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
        project.setDocumentRevision(7);
        when(projectRepository.findById("project-029")).thenReturn(Optional.of(project));
        when(userRepository.findByKeycloakId("user-029")).thenReturn(Optional.of(owner));
    }

    @Test
    void rejectsStaleEditorRevisionBeforeMutatingOrSaving() {
        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .description("must not overwrite newer work")
                .expectedRevision("6")
                .build();

        assertThatThrownBy(() -> service.updateProject("project-029", "user-029", request))
                .isInstanceOfSatisfying(in.voltforge.api.common.exception.ConflictException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo("PROJECT_REVISION_STALE"));
        verify(projectRepository, never()).saveAndFlush(any(Project.class));
        assertThat(project.getDescription()).isNull();
    }

    @Test
    void acceptsMatchingEditorRevisionAndReturnsTheSavedProject() {
        when(projectRepository.saveAndFlush(project)).thenAnswer(call -> {
            project.setDocumentRevision(project.getDocumentRevision() + 1);
            return project;
        });

        UpdateProjectRequest request = UpdateProjectRequest.builder()
                .description("same revision")
                .expectedRevision("7")
                .build();

        var response = service.updateProject("project-029", "user-029", request);

        verify(projectRepository).saveAndFlush(project);
        assertThat(response.getDocumentRevision()).isEqualTo("8");
    }

    @Test
    void rejectsMissingAndLegacyTokensWithoutChangingTheDocument() {
        for (String revision : new String[]{null, "", "2026-08-31T12:00"}) {
            assertThatThrownBy(() -> service.updateProject("project-029", "user-029",
                    UpdateProjectRequest.builder().name("overwrite").expectedRevision(revision).build()))
                    .isInstanceOf(in.voltforge.api.common.exception.ConflictException.class);
        }
        assertThat(project.getName()).isEqualTo("revision test");
        assertThat(project.getDocumentRevision()).isEqualTo(7);
        verify(projectRepository, never()).saveAndFlush(any(Project.class));
    }

    @Test
    void codeOnlySaveAdvancesRevisionAndRejectsReuse() {
        when(projectRepository.saveAndFlush(project)).thenAnswer(call -> {
            project.setDocumentRevision(project.getDocumentRevision() + 1);
            return project;
        });
        var request = UpdateProjectRequest.builder().expectedRevision("7")
                .codeFiles(java.util.List.of(in.voltforge.api.project.dto.CodeFileRequest.builder()
                        .filename("sketch.ino").content("void setup() {} void loop() {}").build())).build();
        var response = service.updateProject("project-029", "user-029", request);
        assertThat(response.getDocumentRevision()).isEqualTo("8");
        assertThat(response.getCodeFiles()).hasSize(1);
        assertThatThrownBy(() -> service.updateProject("project-029", "user-029", request))
                .isInstanceOf(in.voltforge.api.common.exception.ConflictException.class);
    }
}
