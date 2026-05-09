package in.voltforge.api.ai.controller;

import in.voltforge.api.ai.dto.*;
import in.voltforge.api.ai.service.AiService;
import in.voltforge.api.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI-assisted circuit and code generation APIs (powered by Ollama Gemma)")
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
}
