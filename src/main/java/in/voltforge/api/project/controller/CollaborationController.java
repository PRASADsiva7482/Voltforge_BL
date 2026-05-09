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

@Slf4j
@Controller
@RequiredArgsConstructor
public class CollaborationController {

    private final SimpMessageSendingOperations messagingTemplate;

    @MessageMapping("/project/{projectId}/canvas.update")
    public void handleCanvasUpdate(@DestinationVariable String projectId, @Payload CanvasEvent event) {
        log.debug("Canvas event for project {}: {}", projectId, event.getEventType());
        // Broadcast the event to all subscribers of this project's canvas topic
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/canvas", event);
    }

    @MessageMapping("/project/{projectId}/cursor.move")
    public void handleCursorMove(@DestinationVariable String projectId, @Payload CursorEvent event) {
        // Broadcast cursor movements (throttled on client side)
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/cursors", event);
    }
    
    @MessageMapping("/project/{projectId}/simulation.status")
    public void handleSimulationStatus(@DestinationVariable String projectId, @Payload String status) {
        // Broadcast simulation start/stop events
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/simulation", status);
    }
}
