package in.voltforge.api.ai.service;

import in.voltforge.api.ai.dto.*;

public interface AiService {

    AiGenerateResponse generateCircuit(AiGenerateRequest request);

    AiGenerateResponse suggestWiring(AiGenerateRequest request);

    AiGenerateResponse generateCode(AiGenerateRequest request);

    AiChatResponse chat(AiChatRequest request);
}
