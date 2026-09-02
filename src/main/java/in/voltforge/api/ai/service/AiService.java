package in.voltforge.api.ai.service;

import in.voltforge.api.ai.dto.*;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Map;

public interface AiService {

    AiGenerateResponse generateCircuit(AiGenerateRequest request);

    AiGenerateResponse suggestWiring(AiGenerateRequest request);

    AiGenerateResponse generateCode(AiGenerateRequest request);

    AiChatResponse chat(AiChatRequest request);

    /** SSE streaming chat — forwards token-by-token events from the Python AI microservice. */
    Flux<ServerSentEvent<String>> chatStream(AiChatRequest request);

    Map<String, Object> inspectMemory(String userId, String projectId, String sessionId, String projectRevision);

    Map<String, Object> setMemoryPreference(String userId, String projectId, String sessionId, AiMemoryPreferenceRequest request);

    Map<String, Object> createMemoryEntry(String userId, String projectId, String sessionId, AiMemoryWriteRequest request);

    Map<String, Object> correctMemoryEntry(String userId, String projectId, String sessionId, String memoryId, AiMemoryCorrectionRequest request);

    Map<String, Object> deleteMemoryEntry(String userId, String projectId, String sessionId, String memoryId);

    Map<String, Object> clearMemory(String userId, String projectId, String sessionId, String scope);

    AiCodeReviewResponse reviewCode(AiCodeReviewRequest request);

    AiGenerateResponse schematicToCode(AiSchematicToCodeRequest request);

    AiValidatorResponse validateCircuit(AiValidatorRequest request);

    Map<String, Object> getHardwareCoverage();

    Map<String, Object> getComponentCoverage();

    Map<String, Object> runPcbDrc(PcbManufacturingRequest request);

    byte[] exportPcbGerber(PcbManufacturingRequest request);
}
