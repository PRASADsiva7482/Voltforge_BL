package in.voltforge.api.ai.service;

import in.voltforge.api.ai.dto.*;
import reactor.core.publisher.Flux;

public interface AiService {

    AiGenerateResponse generateCircuit(AiGenerateRequest request);

    AiGenerateResponse suggestWiring(AiGenerateRequest request);

    AiGenerateResponse generateCode(AiGenerateRequest request);

    AiChatResponse chat(AiChatRequest request);

    /** SSE streaming chat — forwards token-by-token events from the Python AI microservice. */
    Flux<String> chatStream(AiChatRequest request);

    AiCodeReviewResponse reviewCode(AiCodeReviewRequest request);

    AiGenerateResponse schematicToCode(AiSchematicToCodeRequest request);

    AiValidatorResponse validateCircuit(AiValidatorRequest request);
}
