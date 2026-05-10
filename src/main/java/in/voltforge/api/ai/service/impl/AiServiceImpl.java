package in.voltforge.api.ai.service.impl;

import in.voltforge.api.ai.dto.*;
import in.voltforge.api.ai.service.AiService;
import in.voltforge.api.config.OllamaConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Service implementation using Ollama (Gemma model) for real LLM-powered
 * circuit generation, wiring suggestions, code generation, and interactive chat.
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private final WebClient ollamaWebClient;
    private final OllamaConfig ollamaConfig;
    private final ObjectMapper objectMapper;

    public AiServiceImpl(@Qualifier("ollamaWebClient") WebClient ollamaWebClient,
                         OllamaConfig ollamaConfig,
                         ObjectMapper objectMapper) {
        this.ollamaWebClient = ollamaWebClient;
        this.ollamaConfig = ollamaConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiGenerateResponse generateCircuit(AiGenerateRequest request) {
        log.info("AI circuit generation requested: {}", request.getPrompt());

        String systemPrompt = """
                You are VoltForge AI, an expert electronics circuit designer.
                When asked to design a circuit, respond with:
                1. A brief description of the circuit
                2. A JSON block with "components" (array of {id, type, name}) and "wires" (array of {from, to, color})
                3. Arduino/ESP32 code for the circuit
                
                Component types: ARDUINO_UNO, ARDUINO_MEGA, ARDUINO_NANO, ESP32, ESP32_S3, ESP8266,
                LED_STANDARD, RESISTOR, CAPACITOR, MOTOR_DC, SERVO_MOTOR, BUZZER, PUSH_BUTTON,
                POTENTIOMETER, LDR, TEMP_SENSOR, ULTRASONIC_SENSOR, LCD_16X2, OLED_128X64, RELAY_SPDT
                
                Board type context: %s
                """.formatted(request.getBoardType() != null ? request.getBoardType().name() : "ARDUINO_UNO");

        String aiResponse = callOllama(systemPrompt, request.getPrompt());

        // Parse code from AI response
        String code = extractCodeBlock(aiResponse);
        Map<String, Object> canvasLayout = extractCanvasLayout(aiResponse);

        return AiGenerateResponse.builder()
                .status("SUCCESS")
                .message(cleanMarkdown(aiResponse))
                .canvasLayout(canvasLayout)
                .generatedCode(code.isEmpty() ? generateFallbackCode(request.getPrompt()) : code)
                .build();
    }

    @Override
    public AiGenerateResponse suggestWiring(AiGenerateRequest request) {
        log.info("AI wiring suggestion requested: {}", request.getPrompt());

        String componentList = request.getComponentTypes() != null
                ? String.join(", ", request.getComponentTypes())
                : "unknown components";

        String systemPrompt = """
                You are VoltForge AI, an expert at wiring electronic components.
                Given a list of canvas components and their pins, suggest how to wire them together.
                Respond ONLY with a JSON array of wiring suggestions. Each suggestion must have:
                {
                  "fromComponentId": "exact_canvas_component_id",
                  "fromPin": "exact_pin_id_or_name",
                  "toComponentId": "exact_canvas_component_id",
                  "toPin": "exact_pin_id_or_name",
                  "color": "#hex_color",
                  "description": "brief description"
                }
                Use red (#FF5722) for power, black (#212121) for ground, blue (#2196F3) for signal.
                Prefer exact component ids and pin ids from the user's prompt over generic component type names.
                """;

        String userPrompt = request.getPrompt() + "\n\nComponent summary: " + componentList +
                (request.getBoardType() != null ? " on a " + request.getBoardType().name() : "");

        String aiResponse = callOllama(systemPrompt, userPrompt);

        List<AiWireSuggestion> suggestions = extractWireSuggestions(aiResponse);

        return AiGenerateResponse.builder()
                .status("SUCCESS")
                .message("AI wiring suggestions generated successfully.")
                .wireSuggestions(suggestions)
                .build();
    }

    @Override
    public AiGenerateResponse generateCode(AiGenerateRequest request) {
        log.info("AI code generation requested: {}", request.getPrompt());

        String boardType = request.getBoardType() != null ? request.getBoardType().name() : "ARDUINO_UNO";

        String systemPrompt = """
                You are VoltForge AI, an expert Arduino/ESP32 programmer.
                Generate clean, well-commented %s code based on the user's request.
                Always include proper pin definitions, setup(), and loop() functions.
                Use Serial.begin(9600) for serial communication.
                Add comments explaining each section of the code.
                Respond with ONLY the code, no explanations outside the code block.
                """.formatted(boardType.contains("ESP") ? "ESP32 (Arduino framework)" : "Arduino");

        String aiResponse = callOllama(systemPrompt, request.getPrompt());
        String code = extractCodeBlock(aiResponse);

        if (code.isEmpty()) {
            code = aiResponse.trim();
        }

        return AiGenerateResponse.builder()
                .status("SUCCESS")
                .message("AI code generated successfully.")
                .generatedCode(code)
                .build();
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        log.info("AI chat requested: {}", request.getMessage());

        String systemPrompt = """
                You are VoltForge AI Assistant, an expert in electronics, Arduino, ESP32, and circuit design.
                You help users build and debug their virtual electronics projects.
                Be concise but thorough. When providing code, use markdown code blocks.
                If the user provides context about their current project, use it to give specific advice.
                """;

        // Build conversation history for context
        StringBuilder fullPrompt = new StringBuilder();
        if (request.getContext() != null && !request.getContext().isEmpty()) {
            fullPrompt.append("Current project context:\n").append(request.getContext()).append("\n\n");
        }
        if (request.getHistory() != null) {
            for (AiChatRequest.ChatMessage msg : request.getHistory()) {
                fullPrompt.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            }
        }
        fullPrompt.append("user: ").append(request.getMessage());

        String aiResponse = callOllama(systemPrompt, fullPrompt.toString());

        String code = extractCodeBlock(aiResponse);
        String reply = aiResponse;

        return AiChatResponse.builder()
                .reply(reply)
                .generatedCode(code.isEmpty() ? null : code)
                .hasCode(!code.isEmpty())
                .build();
    }

    // ── Ollama HTTP Call ──────────────────────────────────────────────────────

    private String callOllama(String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", ollamaConfig.getModel());
            requestBody.put("prompt", userPrompt);
            requestBody.put("system", systemPrompt);
            requestBody.put("stream", false);
            requestBody.put("options", Map.of(
                    "temperature", 0.7,
                    "top_p", 0.9,
                    "num_predict", 2048
            ));

            String response = ollamaWebClient.post()
                    .uri("/api/generate")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(ollamaConfig.getTimeoutSeconds()))
                    .block();

            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if (root.has("response")) {
                    return root.get("response").asText();
                }
            }

            return "I apologize, but I couldn't generate a response. Please try again.";

        } catch (WebClientResponseException e) {
            log.error("Ollama API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "AI service is temporarily unavailable. Status: " + e.getStatusCode();
        } catch (Exception e) {
            log.error("Error calling Ollama: {}", e.getMessage(), e);
            return "AI service encountered an error: " + e.getMessage();
        }
    }

    // ── Response Parsers ─────────────────────────────────────────────────────

    private String extractCodeBlock(String response) {
        // Match ```cpp, ```c, ```ino, ```arduino or just ``` code blocks
        Pattern pattern = Pattern.compile("```(?:cpp|c|ino|arduino|python|java)?\\s*\\n(.*?)\\n```",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractCanvasLayout(String response) {
        try {
            // Try to find JSON block in the response
            Pattern jsonPattern = Pattern.compile("\\{[^{}]*\"components\".*?\\}", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(response);
            if (matcher.find()) {
                return objectMapper.readValue(matcher.group(), new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.debug("Could not parse canvas layout from AI response: {}", e.getMessage());
        }
        return new HashMap<>();
    }

    private List<AiWireSuggestion> extractWireSuggestions(String response) {
        List<AiWireSuggestion> suggestions = new ArrayList<>();
        try {
            // Find JSON array in the response
            Pattern jsonArrayPattern = Pattern.compile("\\[\\s*\\{.*?}\\s*]", Pattern.DOTALL);
            Matcher matcher = jsonArrayPattern.matcher(response);
            if (matcher.find()) {
                List<Map<String, String>> parsed = objectMapper.readValue(
                        matcher.group(), new TypeReference<List<Map<String, String>>>() {});
                for (Map<String, String> item : parsed) {
                    suggestions.add(AiWireSuggestion.builder()
                            .fromComponentId(item.getOrDefault("fromComponentId", ""))
                            .fromPin(item.getOrDefault("fromPin", ""))
                            .toComponentId(item.getOrDefault("toComponentId", ""))
                            .toPin(item.getOrDefault("toPin", ""))
                            .color(item.getOrDefault("color", "#3b82f6"))
                            .description(item.getOrDefault("description", ""))
                            .build());
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse wire suggestions from AI response: {}", e.getMessage());
        }

        // If parsing fails, return sensible defaults
        if (suggestions.isEmpty()) {
            suggestions.add(AiWireSuggestion.builder()
                    .fromComponentId("BOARD").fromPin("D13")
                    .toComponentId("LED").toPin("anode")
                    .color("#FF5722").description("Connect digital pin to LED")
                    .build());
        }

        return suggestions;
    }

    private String cleanMarkdown(String text) {
        // Remove code blocks from the message (they're already extracted)
        return text.replaceAll("```(?:cpp|c|ino|arduino)?\\s*\\n.*?\\n```", "[code generated]")
                .trim();
    }

    private String generateFallbackCode(String prompt) {
        return "// AI-generated code for: " + prompt + "\n" +
                "#include <Arduino.h>\n\n" +
                "void setup() {\n" +
                "  Serial.begin(9600);\n" +
                "  Serial.println(\"VoltForge AI — Ready\");\n" +
                "}\n\n" +
                "void loop() {\n" +
                "  // Your logic here\n" +
                "  delay(1000);\n" +
                "}\n";
    }

    @Override
    public AiCodeReviewResponse reviewCode(AiCodeReviewRequest request) {
        log.info("AI code review requested for board: {}", request.getBoardType());

        String systemPrompt = """
                You are VoltForge AI Code Reviewer. Analyze the Arduino/ESP32 code and respond with:
                1. A brief summary of what the code does
                2. A JSON block with format: {"score": 0-100, "issues": [{"severity":"ERROR|WARNING|INFO","line":1,"message":"...","fix":"..."}], "suggestions": ["..."]}
                3. An improved version of the code in a code block
                Be strict about pin safety, memory leaks, and timing issues.
                """;

        String userPrompt = String.format("Board: %s\nComponents: %s\n\nCode:\n```\n%s\n```",
                request.getBoardType(),
                request.getComponentTypes() != null ? String.join(", ", request.getComponentTypes()) : "unknown",
                request.getCode());

        String response = callOllama(systemPrompt, userPrompt);

        // Parse response
        List<AiCodeReviewResponse.ReviewIssue> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        int score = 70;

        try {
            Pattern jsonPattern = Pattern.compile("\\{[^{}]*\"score\".*?\\}", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(response);
            if (matcher.find()) {
                JsonNode node = objectMapper.readTree(matcher.group());
                if (node.has("score")) score = node.get("score").asInt();
                if (node.has("issues")) {
                    for (JsonNode issue : node.get("issues")) {
                        issues.add(AiCodeReviewResponse.ReviewIssue.builder()
                                .severity(issue.has("severity") ? issue.get("severity").asText() : "INFO")
                                .line(issue.has("line") ? issue.get("line").asInt() : 0)
                                .message(issue.has("message") ? issue.get("message").asText() : "")
                                .fix(issue.has("fix") ? issue.get("fix").asText() : "")
                                .build());
                    }
                }
                if (node.has("suggestions")) {
                    for (JsonNode s : node.get("suggestions")) {
                        suggestions.add(s.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse code review JSON: {}", e.getMessage());
            suggestions.add("Review completed but detailed parsing failed.");
        }

        String improvedCode = extractCodeBlock(response);

        return AiCodeReviewResponse.builder()
                .summary(cleanMarkdown(response).substring(0, Math.min(cleanMarkdown(response).length(), 500)))
                .issues(issues)
                .suggestions(suggestions)
                .improvedCode(improvedCode)
                .score(score)
                .build();
    }

    @Override
    public AiGenerateResponse schematicToCode(AiSchematicToCodeRequest request) {
        log.info("AI schematic-to-code requested for board: {}", request.getBoardType());

        StringBuilder componentDesc = new StringBuilder();
        if (request.getComponents() != null) {
            for (AiSchematicToCodeRequest.ComponentInfo c : request.getComponents()) {
                componentDesc.append(String.format("- %s (%s)", c.getName(), c.getType()));
                if (c.getProperties() != null) componentDesc.append(" props: ").append(c.getProperties());
                componentDesc.append("\n");
            }
        }

        StringBuilder wireDesc = new StringBuilder();
        if (request.getWires() != null) {
            for (AiSchematicToCodeRequest.WireInfo w : request.getWires()) {
                wireDesc.append(String.format("- %s.%s -> %s.%s\n", w.getFromComponent(), w.getFromPin(), w.getToComponent(), w.getToPin()));
            }
        }

        String systemPrompt = """
                You are VoltForge AI, an expert Arduino/ESP32 programmer.
                Generate complete, working code based on the circuit schematic provided.
                Include proper pin definitions, setup(), and loop() functions.
                Add helpful comments explaining each section.
                Use appropriate libraries for the components listed.
                """;

        String userPrompt = String.format("Board: %s\n\nComponents:\n%s\nWiring:\n%s\n%s",
                request.getBoardType(), componentDesc, wireDesc,
                request.getAdditionalInstructions() != null ? "Instructions: " + request.getAdditionalInstructions() : "");

        String response = callOllama(systemPrompt, userPrompt);
        String code = extractCodeBlock(response);
        if (code.isEmpty()) code = generateFallbackCode("schematic-to-code");

        return AiGenerateResponse.builder()
                .status("success")
                .message(cleanMarkdown(response))
                .generatedCode(code)
                .build();
    }

    @Override
    public AiValidatorResponse validateCircuit(AiValidatorRequest request) {
        log.info("AI circuit validation requested for board: {}", request.getBoardType());

        StringBuilder componentDesc = new StringBuilder();
        if (request.getComponents() != null) {
            for (AiValidatorRequest.ComponentInfo c : request.getComponents()) {
                componentDesc.append(String.format("- %s (%s, ID: %s)\n", c.getName(), c.getType(), c.getId()));
            }
        }

        StringBuilder wireDesc = new StringBuilder();
        if (request.getWires() != null) {
            for (AiValidatorRequest.WireInfo w : request.getWires()) {
                wireDesc.append(String.format("- %s.%s -> %s.%s\n", w.getFromComponent(), w.getFromPin(), w.getToComponent(), w.getToPin()));
            }
        }

        String systemPrompt = """
                You are VoltForge AI Circuit Validator. Analyze the schematic and respond with:
                1. A brief general feedback string.
                2. A JSON block with format: {"isValid": true/false, "safetyScore": 0-100, "issues": [{"severity":"CRITICAL|WARNING|INFO","componentId":"...","message":"...","suggestedFix":"..."}]}
                Check for missing ground/power, incorrect LED wiring (no resistor), short circuits, and improper voltage levels.
                """;

        String userPrompt = String.format("Board: %s\n\nComponents:\n%s\nWiring:\n%s",
                request.getBoardType(), componentDesc, wireDesc);

        String response = callOllama(systemPrompt, userPrompt);

        // Parse response
        List<AiValidatorResponse.ValidationIssue> issues = new ArrayList<>();
        boolean isValid = true;
        int safetyScore = 100;

        try {
            Pattern jsonPattern = Pattern.compile("\\{[^{}]*\"safetyScore\".*?\\}", Pattern.DOTALL);
            Matcher matcher = jsonPattern.matcher(response);
            if (matcher.find()) {
                JsonNode node = objectMapper.readTree(matcher.group());
                if (node.has("isValid")) isValid = node.get("isValid").asBoolean();
                if (node.has("safetyScore")) safetyScore = node.get("safetyScore").asInt();
                if (node.has("issues")) {
                    for (JsonNode issue : node.get("issues")) {
                        issues.add(AiValidatorResponse.ValidationIssue.builder()
                                .severity(issue.has("severity") ? issue.get("severity").asText() : "INFO")
                                .componentId(issue.has("componentId") ? issue.get("componentId").asText() : "")
                                .message(issue.has("message") ? issue.get("message").asText() : "")
                                .suggestedFix(issue.has("suggestedFix") ? issue.get("suggestedFix").asText() : "")
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse validator JSON: {}", e.getMessage());
        }

        return AiValidatorResponse.builder()
                .isValid(isValid)
                .safetyScore(safetyScore)
                .issues(issues)
                .generalFeedback(cleanMarkdown(response).substring(0, Math.min(cleanMarkdown(response).length(), 500)))
                .build();
    }
}
