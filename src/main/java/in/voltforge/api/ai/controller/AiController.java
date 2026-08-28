package in.voltforge.api.ai.controller;

import in.voltforge.api.ai.dto.*;
import in.voltforge.api.ai.service.AiService;
import in.voltforge.api.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI-assisted circuit and code generation APIs (powered by VoltForge AI)")
public class AiController {

    private final AiService aiService;

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
            @Valid @RequestBody AiChatRequest request) {
        AiChatResponse response = aiService.chat(request);
        return ResponseEntity.ok(ApiResponse.success("AI response generated", response));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Streaming chat with VoltForge AI — SSE token-by-token with thinking")
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody AiChatRequest request) {
        return aiService.chatStream(request);
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
