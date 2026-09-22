package in.voltforge.api.ai.controller;

import tools.jackson.databind.json.JsonMapper;
import in.voltforge.api.ai.dto.AiChatRequest;
import in.voltforge.api.ai.dto.AiChatResponse;
import in.voltforge.api.ai.gateway.AiGatewayException;
import in.voltforge.api.ai.gateway.AiGatewayPolicy;
import in.voltforge.api.ai.service.AiService;
import in.voltforge.api.config.VoltforgeAiConfig;
import in.voltforge.api.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;

class AiControllerTest {

    private AiService aiService;
    private ProjectService projectService;
    private AiController controller;

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        projectService = mock(ProjectService.class);
        controller = new AiController(aiService, projectService,
                new AiGatewayPolicy(new JsonMapper(), new VoltforgeAiConfig()));
    }

    @Test
    void bindsJwtIdentityAndAllowsTheCurrentProjectRevision() {
        when(projectService.canAccessProject("project-027", "user-027")).thenReturn(true);
        when(projectService.getProjectRevision("project-027", "user-027")).thenReturn("revision-027");
        when(aiService.chat(any())).thenReturn(AiChatResponse.builder().reply("safe").build());

        AiChatRequest request = AiChatRequest.builder()
                .message("Explain this circuit")
                .authenticatedUserId("forged-user")
                .projectId("project-027")
                .projectRevision("revision-027")
                .build();

        assertThat(controller.chat(request, jwt("user-027"))).isNotNull();
        assertThat(request.getAuthenticatedUserId()).isEqualTo("user-027");
        verify(aiService).chat(request);
    }

    @Test
    void rejectsCrossProjectAccessBeforeCallingTheAiService() {
        when(projectService.canAccessProject("project-other", "user-027")).thenReturn(false);
        AiChatRequest request = AiChatRequest.builder()
                .message("Use the other project")
                .projectId("project-other")
                .build();

        assertThatThrownBy(() -> controller.chat(request, jwt("user-027")))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(error -> {
                    AiGatewayException gatewayError = (AiGatewayException) error;
                    assertThat(gatewayError.getErrorCode()).isEqualTo("AI_PROJECT_ACCESS_DENIED");
                });
        verifyNoInteractions(aiService);
    }

    @Test
    void exposesHardwareCoverageThroughTheAuthenticatedBackendBoundary() {
        when(aiService.getHardwareCoverage()).thenReturn(Map.of(
                "reportId", "vfai-fu-001-ui-hardware-coverage",
                "entryCount", 49));

        assertThat(controller.hardwareCoverage().getBody().getData())
                .containsEntry("reportId", "vfai-fu-001-ui-hardware-coverage")
                .containsEntry("entryCount", 49);
        verify(aiService).getHardwareCoverage();
    }

    @Test
    void exposesComponentCoverageThroughTheAuthenticatedBackendBoundary() {
        when(aiService.getComponentCoverage()).thenReturn(Map.of(
                "reportId", "vfai-fu-011-ui-component-coverage",
                "entryCount", 60));

        assertThat(controller.componentCoverage().getBody().getData())
                .containsEntry("reportId", "vfai-fu-011-ui-component-coverage")
                .containsEntry("entryCount", 60);
        verify(aiService).getComponentCoverage();
    }

    @Test
    void rejectsAStaleProjectRevisionBeforeForwardingContext() {
        when(projectService.canAccessProject("project-027", "user-027")).thenReturn(true);
        when(projectService.getProjectRevision("project-027", "user-027")).thenReturn("revision-current");
        AiChatRequest request = AiChatRequest.builder()
                .message("Suggest a safe resistor")
                .projectId("project-027")
                .projectRevision("revision-old")
                .build();

        assertThatThrownBy(() -> controller.chatStream(request, jwt("user-027")))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(error -> assertThat(((AiGatewayException) error).getErrorCode())
                        .isEqualTo("AI_PROJECT_REVISION_STALE"));
        verifyNoInteractions(aiService);
    }

    @Test
    void rejectsStructuredContextCopiedFromAnotherProject() {
        when(projectService.canAccessProject("project-027", "user-027")).thenReturn(true);
        AiChatRequest request = AiChatRequest.builder()
                .message("Review this")
                .projectId("project-027")
                .context("{\"projectId\":\"project-other\",\"code\":\"void setup(){}\"}")
                .build();

        assertThatThrownBy(() -> controller.chat(request, jwt("user-027")))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(error -> assertThat(((AiGatewayException) error).getErrorCode())
                        .isEqualTo("AI_PROJECT_CONTEXT_MISMATCH"));
        verifyNoInteractions(aiService);
    }

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .build();
    }
}
