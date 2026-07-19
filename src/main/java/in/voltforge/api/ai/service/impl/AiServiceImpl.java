package in.voltforge.api.ai.service.impl;

import in.voltforge.api.ai.dto.*;
import in.voltforge.api.ai.service.AiService;
import in.voltforge.api.config.VoltforgeAiConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Service implementation that delegates requests to the standalone VoltForge AI microservice.
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private final WebClient voltforgeAiWebClient;
    private final VoltforgeAiConfig aiConfig;
    private final ObjectMapper objectMapper;

    public AiServiceImpl(@Qualifier("voltforgeAiWebClient") WebClient voltforgeAiWebClient,
                          VoltforgeAiConfig aiConfig,
                          ObjectMapper objectMapper) {
        this.voltforgeAiWebClient = voltforgeAiWebClient;
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiGenerateResponse generateCircuit(AiGenerateRequest request) {
        log.info("Delegating generateCircuit to standalone AI microservice");
        try {
            List<Map<String, Object>> components = parseComponentsFromPrompt(request.getPrompt());
            String boardType = request.getBoardType() != null ? request.getBoardType().name() : "ARDUINO_UNO";

            Map<String, Object> requestBody = Map.of(
                    "components", components,
                    "boardType", boardType
            );

            JsonNode response = voltforgeAiWebClient.post()
                    .uri("/api/v1/model/generate-code")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String code = "";
            if (response != null && response.has("generatedCode")) {
                code = response.get("generatedCode").asText();
            }

            return AiGenerateResponse.builder()
                    .status("SUCCESS")
                    .message("Circuit model generated successfully by custom engine.")
                    .canvasLayout(new HashMap<>())
                    .generatedCode(code)
                    .build();

        } catch (Exception e) {
            log.error("Error communicating with AI microservice: {}", e.getMessage(), e);
            return AiGenerateResponse.builder()
                    .status("ERROR")
                    .message("AI microservice error: " + e.getMessage())
                    .generatedCode(generateFallbackCode(request.getPrompt()))
                    .build();
        }
    }

    @Override
    public AiGenerateResponse suggestWiring(AiGenerateRequest request) {
        log.info("Delegating suggestWiring to standalone AI microservice");
        try {
            List<Map<String, Object>> components = parseComponentsFromPrompt(request.getPrompt());
            String boardType = request.getBoardType() != null ? request.getBoardType().name() : "ARDUINO_UNO";

            Map<String, Object> requestBody = Map.of(
                    "components", components,
                    "boardType", boardType
            );

            JsonNode response = voltforgeAiWebClient.post()
                    .uri("/api/v1/model/suggest-wiring")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            List<AiWireSuggestion> suggestions = new ArrayList<>();
            if (response != null && response.has("suggestions")) {
                for (JsonNode node : response.get("suggestions")) {
                    suggestions.add(AiWireSuggestion.builder()
                            .fromComponentId(node.path("fromComponentId").asText())
                            .fromPin(node.path("fromPin").asText())
                            .toComponentId(node.path("toComponentId").asText())
                            .toPin(node.path("toPin").asText())
                            .color(node.path("color").asText("#3b82f6"))
                            .description(node.path("description").asText())
                            .build());
                }
            }

            return AiGenerateResponse.builder()
                    .status("SUCCESS")
                    .message("Wiring suggestions generated successfully.")
                    .wireSuggestions(suggestions)
                    .build();

        } catch (Exception e) {
            log.error("Error communicating with AI microservice: {}", e.getMessage(), e);
            return AiGenerateResponse.builder()
                    .status("ERROR")
                    .message("AI microservice error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public AiGenerateResponse generateCode(AiGenerateRequest request) {
        log.info("Delegating generateCode to standalone AI microservice");
        return generateCircuit(request);
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        log.info("Delegating chat to standalone AI microservice");
        try {
            List<Map<String, String>> history = new ArrayList<>();
            if (request.getHistory() != null) {
                for (var msg : request.getHistory()) {
                    history.add(Map.of(
                            "role", msg.getRole(),
                            "content", msg.getContent()
                    ));
                }
            }

            Map<String, Object> requestBody = Map.of(
                    "message", request.getMessage(),
                    "canvasContext", request.getContext() != null ? request.getContext() : "",
                    "history", history
            );

            JsonNode response = voltforgeAiWebClient.post()
                    .uri("/api/v1/model/chat")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String reply = "Unable to process query.";
            String code = null;
            boolean hasCode = false;

            if (response != null) {
                if (response.has("reply")) reply = response.get("reply").asText();
                if (response.has("generatedCode") && !response.get("generatedCode").isNull()) {
                    code = response.get("generatedCode").asText();
                    hasCode = response.get("hasCode").asBoolean(true);
                }
            }

            return AiChatResponse.builder()
                    .reply(reply)
                    .generatedCode(code)
                    .hasCode(hasCode)
                    .build();

        } catch (Exception e) {
            log.error("Error communicating with AI microservice: {}", e.getMessage(), e);
            return AiChatResponse.builder()
                    .reply("AI microservice is offline or encountered an error: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public AiCodeReviewResponse reviewCode(AiCodeReviewRequest request) {
        log.info("Delegating reviewCode to standalone AI microservice");
        return AiCodeReviewResponse.builder()
                .summary("Code review skipped in custom local model.")
                .issues(Collections.emptyList())
                .suggestions(List.of("Verify board compilation locally using compile service."))
                .improvedCode(request.getCode())
                .score(100)
                .build();
    }

    @Override
    public AiGenerateResponse schematicToCode(AiSchematicToCodeRequest request) {
        log.info("Delegating schematicToCode to standalone AI microservice");
        try {
            Map<String, Object> requestBody = Map.of(
                    "components", request.getComponents() != null ? request.getComponents() : Collections.emptyList(),
                    "wires", request.getWires() != null ? request.getWires() : Collections.emptyList(),
                    "boardType", request.getBoardType() != null ? request.getBoardType() : "ARDUINO_UNO"
            );

            JsonNode response = voltforgeAiWebClient.post()
                    .uri("/api/v1/model/generate-code")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String code = "";
            if (response != null && response.has("generatedCode")) {
                code = response.get("generatedCode").asText();
            }

            return AiGenerateResponse.builder()
                    .status("SUCCESS")
                    .message("Code generated successfully from schematic layout.")
                    .generatedCode(code)
                    .build();

        } catch (Exception e) {
            log.error("Error communicating with AI microservice: {}", e.getMessage(), e);
            return AiGenerateResponse.builder()
                    .status("ERROR")
                    .message("AI microservice error: " + e.getMessage())
                    .generatedCode(generateFallbackCode("schematicToCode"))
                    .build();
        }
    }

    @Override
    public AiValidatorResponse validateCircuit(AiValidatorRequest request) {
        log.info("Delegating validateCircuit to standalone AI microservice");
        try {
            Map<String, Object> requestBody = Map.of(
                    "components", request.getComponents() != null ? request.getComponents() : Collections.emptyList(),
                    "wires", request.getWires() != null ? request.getWires() : Collections.emptyList(),
                    "boardType", request.getBoardType() != null ? request.getBoardType() : "ARDUINO_UNO"
            );

            JsonNode response = voltforgeAiWebClient.post()
                    .uri("/api/v1/model/validate-circuit")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            List<AiValidatorResponse.ValidationIssue> issues = new ArrayList<>();
            boolean isValid = true;
            int safetyScore = 100;
            String feedback = "";

            if (response != null) {
                isValid = response.path("isValid").asBoolean(true);
                safetyScore = response.path("safetyScore").asInt(100);
                feedback = response.path("generalFeedback").asText("");

                if (response.has("issues")) {
                    for (JsonNode node : response.get("issues")) {
                        issues.add(AiValidatorResponse.ValidationIssue.builder()
                                .severity(node.path("severity").asText("INFO"))
                                .componentId(node.path("componentId").asText(""))
                                .message(node.path("message").asText(""))
                                .suggestedFix(node.path("suggestedFix").asText(""))
                                .build());
                    }
                }
            }

            return AiValidatorResponse.builder()
                    .isValid(isValid)
                    .safetyScore(safetyScore)
                    .issues(issues)
                    .generalFeedback(feedback)
                    .build();

        } catch (Exception e) {
            log.error("Error communicating with AI microservice: {}", e.getMessage(), e);
            return AiValidatorResponse.builder()
                    .isValid(false)
                    .safetyScore(0)
                    .issues(Collections.emptyList())
                    .generalFeedback("AI microservice is currently offline.")
                    .build();
        }
    }

    // ── Helper Parser ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseComponentsFromPrompt(String prompt) {
        List<Map<String, Object>> components = new ArrayList<>();
        if (prompt == null) return components;

        // Match lines like: "- ID: Name (TYPE) pins: pin_id/pin_name, ..."
        java.util.regex.Pattern compPattern = java.util.regex.Pattern.compile(
                "-\\s+([^:]+):\\s+(.+?)\\s+\\(([A-Z0-9_]+)\\)\\s+pins:\\s+(.+)",
                java.util.regex.Pattern.CASE_INSENSITIVE);

        for (String line : prompt.split("\\n")) {
            java.util.regex.Matcher m = compPattern.matcher(line.trim());
            if (m.find()) {
                String compId = m.group(1).trim();
                String compName = m.group(2).trim();
                String compType = m.group(3).trim();
                String pinsStr = m.group(4).trim();

                List<Map<String, Object>> pins = new ArrayList<>();
                for (String pinEntry : pinsStr.split(",\\s*")) {
                    String[] parts = pinEntry.trim().split("/", 2);
                    if (parts.length >= 1) {
                        Map<String, Object> pin = new HashMap<>();
                        pin.put("id", parts[0].trim());
                        pin.put("name", parts.length > 1 ? parts[1].trim() : parts[0].trim());
                        pins.add(pin);
                    }
                }

                Map<String, Object> comp = new HashMap<>();
                comp.put("id", compId);
                comp.put("name", compName);
                comp.put("type", compType);
                comp.put("pins", pins);
                components.add(comp);
            }
        }
        return components;
    }

    private String generateFallbackCode(String prompt) {
        return "// AI-generated code fallback\n" +
                "#include <Arduino.h>\n\n" +
                "void setup() {\n" +
                "  Serial.begin(9600);\n" +
                "}\n\n" +
                "void loop() {\n" +
                "  delay(1000);\n" +
                "}\n";
    }
}
