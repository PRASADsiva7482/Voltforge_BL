package in.voltforge.api.ai.controller;

import in.voltforge.api.ai.dto.*;
import in.voltforge.api.ai.gateway.AiGatewayException;
import in.voltforge.api.ai.gateway.AiGatewayPolicy;
import in.voltforge.api.ai.service.AiService;
import in.voltforge.api.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;
import in.voltforge.api.project.service.ProjectService;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI-assisted circuit and code generation APIs (powered by VoltForge AI)")
public class AiController {

    private final AiService aiService;
    private final ProjectService projectService;
    private final AiGatewayPolicy gatewayPolicy;

    @PostMapping("/generate-circuit")
    @Operation(summary = "Generate circuit using AI")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> generateCircuit(
            @Valid @RequestBody AiGenerateRequest request) {
        AiGenerateResponse response = aiService.generateCircuit(request);
        return ResponseEntity.ok(ApiResponse.success("Circuit generated", response));
    }

    @PostMapping("/suggest-wiring")
    @Operation(summary = "Get AI wiring suggestions")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> suggestWiring(
            @Valid @RequestBody AiGenerateRequest request) {
        AiGenerateResponse response = aiService.suggestWiring(request);
        return ResponseEntity.ok(ApiResponse.success("Wiring suggestions generated", response));
    }

    @PostMapping("/generate-code")
    @Operation(summary = "Generate code using AI")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> generateCode(
            @Valid @RequestBody AiGenerateRequest request) {
        AiGenerateResponse response = aiService.generateCode(request);
        return ResponseEntity.ok(ApiResponse.success("Code generated", response));
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat with VoltForge AI Assistant")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        bindAuthenticatedScope(request, jwt);
        AiChatResponse response = aiService.chat(request);
        return ResponseEntity.ok(ApiResponse.success("AI response generated", response));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Streaming chat with VoltForge AI — SSE token-by-token with thinking")
    public Flux<ServerSentEvent<String>> chatStream(
            @Valid @RequestBody AiChatRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        bindAuthenticatedScope(request, jwt);
        return aiService.chatStream(request);
    }

    @GetMapping("/hardware-coverage")
    @Operation(summary = "Get exact-variant AI hardware coverage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> hardwareCoverage() {
        return ResponseEntity.ok(ApiResponse.success("AI hardware coverage loaded",
                aiService.getHardwareCoverage()));
    }

    @GetMapping("/component-coverage")
    @Operation(summary = "Get fail-closed AI component coverage")
    public ResponseEntity<ApiResponse<Map<String, Object>>> componentCoverage() {
        return ResponseEntity.ok(ApiResponse.success("AI component coverage loaded",
                aiService.getComponentCoverage()));
    }

    @GetMapping("/memory")
    @Operation(summary = "Inspect bounded AI memory for the authenticated project scope")
    public ResponseEntity<ApiResponse<Map<String, Object>>> inspectMemory(
            @RequestParam String projectId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String projectRevision,
            @AuthenticationPrincipal Jwt jwt) {
        requireProjectAccess(projectId, jwt);
        return ResponseEntity.ok(ApiResponse.success("AI memory inspected",
                aiService.inspectMemory(jwt.getSubject(), projectId, sessionId, projectRevision)));
    }

    @PutMapping("/memory/preferences")
    @Operation(summary = "Enable or disable bounded AI memory")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setMemoryPreference(
            @RequestParam String projectId,
            @RequestParam(required = false) String sessionId,
            @Valid @RequestBody AiMemoryPreferenceRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        requireProjectAccess(projectId, jwt);
        return ResponseEntity.ok(ApiResponse.success("AI memory preference updated",
                aiService.setMemoryPreference(jwt.getSubject(), projectId, sessionId, request)));
    }

    @PostMapping("/memory/entries")
    @Operation(summary = "Create an explicitly approved bounded memory entry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMemoryEntry(
            @RequestParam String projectId,
            @RequestParam(required = false) String sessionId,
            @Valid @RequestBody AiMemoryWriteRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        requireProjectAccess(projectId, jwt);
        return ResponseEntity.ok(ApiResponse.success("AI memory entry created",
                aiService.createMemoryEntry(jwt.getSubject(), projectId, sessionId, request)));
    }

    @PatchMapping("/memory/entries/{memoryId}")
    @Operation(summary = "Correct a versioned bounded memory entry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> correctMemoryEntry(
            @PathVariable String memoryId,
            @RequestParam String projectId,
            @RequestParam(required = false) String sessionId,
            @Valid @RequestBody AiMemoryCorrectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        requireProjectAccess(projectId, jwt);
        return ResponseEntity.ok(ApiResponse.success("AI memory entry corrected",
                aiService.correctMemoryEntry(jwt.getSubject(), projectId, sessionId, memoryId, request)));
    }

    @DeleteMapping("/memory/entries/{memoryId}")
    @Operation(summary = "Delete one bounded memory entry")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteMemoryEntry(
            @PathVariable String memoryId,
            @RequestParam String projectId,
            @RequestParam(required = false) String sessionId,
            @AuthenticationPrincipal Jwt jwt) {
        requireProjectAccess(projectId, jwt);
        return ResponseEntity.ok(ApiResponse.success("AI memory entry deleted",
                aiService.deleteMemoryEntry(jwt.getSubject(), projectId, sessionId, memoryId)));
    }

    @DeleteMapping("/memory")
    @Operation(summary = "Clear session or project bounded memory")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearMemory(
            @RequestParam String projectId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "session") String scope,
            @AuthenticationPrincipal Jwt jwt) {
        requireProjectAccess(projectId, jwt);
        if (!scope.equals("session") && !scope.equals("project")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Memory clear scope is invalid");
        }
        return ResponseEntity.ok(ApiResponse.success("AI memory cleared",
                aiService.clearMemory(jwt.getSubject(), projectId, sessionId, scope)));
    }

    private void bindAuthenticatedScope(AiChatRequest request, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new AiGatewayException(HttpStatus.UNAUTHORIZED,
                    "AI_AUTHENTICATION_REQUIRED", "Authenticated AI identity required", false);
        }
        request.setAuthenticatedUserId(jwt.getSubject());
        if (request.getProjectId() != null) {
            requireProjectAccess(request.getProjectId(), jwt);
            validateCurrentRevision(request, jwt);
        }
        gatewayPolicy.validateChatRequest(request);
    }

    private void requireProjectAccess(String projectId, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null
                || !projectService.canAccessProject(projectId, jwt.getSubject())) {
            throw new AiGatewayException(HttpStatus.FORBIDDEN,
                    "AI_PROJECT_ACCESS_DENIED", "Project access denied", false);
        }
    }

    private void validateCurrentRevision(AiChatRequest request, Jwt jwt) {
        if (request.getProjectRevision() == null || request.getProjectRevision().isBlank()) {
            return;
        }
        String currentRevision = projectService.getProjectRevision(
                request.getProjectId(), jwt.getSubject());
        if (currentRevision != null && !currentRevision.equals(request.getProjectRevision())) {
            throw new AiGatewayException(HttpStatus.CONFLICT,
                    "AI_PROJECT_REVISION_STALE",
                    "The project changed while this AI request was being prepared; regenerate from the current project",
                    false);
        }
    }

    @PostMapping("/review-code")
    @Operation(summary = "AI-powered code review and analysis")
    public ResponseEntity<ApiResponse<AiCodeReviewResponse>> reviewCode(
            @Valid @RequestBody AiCodeReviewRequest request) {
        AiCodeReviewResponse response = aiService.reviewCode(request);
        return ResponseEntity.ok(ApiResponse.success("Code review completed", response));
    }

    @PostMapping("/schematic-to-code")
    @Operation(summary = "Generate code from circuit schematic layout")
    public ResponseEntity<ApiResponse<AiGenerateResponse>> schematicToCode(
            @Valid @RequestBody AiSchematicToCodeRequest request) {
        AiGenerateResponse response = aiService.schematicToCode(request);
        return ResponseEntity.ok(ApiResponse.success("Code generated from schematic", response));
    }

    @PostMapping("/validate-circuit")
    @Operation(summary = "Validate circuit safety and logic")
    public ResponseEntity<ApiResponse<AiValidatorResponse>> validateCircuit(
            @Valid @RequestBody AiValidatorRequest request) {
        AiValidatorResponse response = aiService.validateCircuit(request);
        return ResponseEntity.ok(ApiResponse.success("Circuit validation completed", response));
    }

    @PostMapping("/circuit/drc-check")
    @Operation(summary = "Run PCB design rule checks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runPcbDrc(
            @RequestBody PcbManufacturingRequest request) {
        Map<String, Object> response = aiService.runPcbDrc(request);
        return ResponseEntity.ok(ApiResponse.success("PCB DRC completed", response));
    }

    @PostMapping(value = "/circuit/export-gerber", produces = "application/zip")
    @Operation(summary = "Export PCB manufacturing Gerber ZIP")
    public ResponseEntity<byte[]> exportPcbGerber(
            @RequestBody PcbManufacturingRequest request) {
        byte[] zip = aiService.exportPcbGerber(request);
        String name = request != null && request.getProjectName() != null && !request.getProjectName().isBlank()
                ? request.getProjectName().replaceAll("[^A-Za-z0-9_.-]", "_")
                : "VoltForge_PCB";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + name + "_gerber.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }
}
