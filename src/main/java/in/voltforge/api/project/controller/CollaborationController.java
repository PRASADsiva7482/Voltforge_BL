package in.voltforge.api.project.controller;

import in.voltforge.api.project.dto.CanvasEvent;
import in.voltforge.api.project.dto.CursorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CollaborationController {

    private final SimpMessageSendingOperations messagingTemplate;

    @MessageMapping("/project/{projectId}/canvas.update")
    public void handleCanvasUpdate(
            @DestinationVariable String projectId,
            @Payload CanvasEvent event,
            Principal principal
    ) {
        event.setProjectId(projectId);
        event.setUserId(resolveUserId(principal, event.getUserId()));
        if (event.getTimestamp() == null) {
            event.setTimestamp(System.currentTimeMillis());
        }

        log.debug("Canvas event for project {}: {}", projectId, event.getEventType());
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/canvas", event);
    }

    @MessageMapping("/project/{projectId}/cursor.move")
    public void handleCursorMove(
            @DestinationVariable String projectId,
            @Payload CursorEvent event,
            Principal principal
    ) {
        event.setProjectId(projectId);
        event.setUserId(resolveUserId(principal, event.getUserId()));
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/cursors", event);
    }

    @MessageMapping("/project/{projectId}/simulation.status")
    public void handleSimulationStatus(@DestinationVariable String projectId, @Payload String status) {
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/simulation", status);
    }

    private String resolveUserId(Principal principal, String fallbackUserId) {
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return principal.getName();
        }
        return fallbackUserId;
    }
}
