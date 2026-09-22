package in.voltforge.api.project.controller;

import in.voltforge.api.project.dto.CanvasEvent;
import in.voltforge.api.project.dto.CursorEvent;
import in.voltforge.api.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CollaborationController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final ProjectService projectService;

    @MessageMapping("/project/{projectId}/canvas.update")
    public void handleCanvasUpdate(
            @DestinationVariable String projectId,
            @Payload CanvasEvent event,
            Principal principal
    ) {
        requireEditAccess(projectId, principal);
        if (event == null) {
            throw new MessagingException("Canvas event is required");
        }
        event.setProjectId(projectId);
        event.setUserId(principal.getName());
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }

        log.debug("Canvas event for project {}: {}", projectId, event.getEventType());
        // Live previews are transient. The editor persists the whole document via
        // the revision-guarded REST save and receives its new revision there.
        // A second delayed writer here can overwrite a newer save without an ack.
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/canvas", event);
    }

    @MessageMapping("/project/{projectId}/cursor.move")
    public void handleCursorMove(
            @DestinationVariable String projectId,
            @Payload CursorEvent event,
            Principal principal
    ) {
        requireAccess(projectId, principal);
        event.setProjectId(projectId);
        event.setUserId(principal.getName());
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/cursors", event);
    }

    @MessageMapping("/project/{projectId}/simulation.status")
    public void handleSimulationStatus(@DestinationVariable String projectId, @Payload String status, Principal principal) {
        requireEditAccess(projectId, principal);
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/simulation", status);
    }

    private void requireAccess(String projectId, Principal principal) {
        if (principal == null || principal.getName() == null
                || !projectService.canAccessProject(projectId, principal.getName())) {
            throw new MessagingException("Access denied to project " + projectId);
        }
    }

    private void requireEditAccess(String projectId, Principal principal) {
        if (principal == null || principal.getName() == null
                || !projectService.canEditProject(projectId, principal.getName())) {
            throw new MessagingException("Edit access denied to project " + projectId);
        }
    }
}
