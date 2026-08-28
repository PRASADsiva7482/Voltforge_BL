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

    AiCodeReviewResponse reviewCode(AiCodeReviewRequest request);

    AiGenerateResponse schematicToCode(AiSchematicToCodeRequest request);

    AiValidatorResponse validateCircuit(AiValidatorRequest request);

    Map<String, Object> runPcbDrc(PcbManufacturingRequest request);

    byte[] exportPcbGerber(PcbManufacturingRequest request);
}
