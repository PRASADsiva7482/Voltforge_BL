package in.voltforge.api.ai.gateway;

import in.voltforge.api.ai.dto.AiResearchRequest;
import org.springframework.http.HttpStatus;

/** Require explicit public-query consent; unsupported retrieval modes fail closed. */
public final class AiResearchPolicy {
    private AiResearchPolicy() {}
    public static void validate(AiResearchRequest request) {
        if (request == null) return;
        boolean hasQuery = request.publicQuery() != null && !request.publicQuery().isBlank();
        if (request.publicQuery() != null && request.publicQuery().length() > 500
                || request.documentIds().size() > 10
                || request.documentIds().stream().anyMatch(id -> id.isBlank() || id.length() > 80)) {
            throw invalid("Research input exceeds the permitted limits");
        }
        if (request.mode() == AiResearchRequest.Mode.off) {
            if (hasQuery || request.consentToExternalQuery()) throw invalid("Research is off; remove the external query and consent");
        } else if (!hasQuery || !request.consentToExternalQuery()) {
            throw invalid("Review a public query and explicitly permit sending it before web research");
        }
        if (new java.util.HashSet<>(request.documentIds()).size()!=request.documentIds().size()) throw invalid("Choose distinct document IDs");
        if (hasQuery && request.publicQuery().split("\\s+").length > 75) throw invalid("Keep the public search query within 75 words");
        if (request.mode() == AiResearchRequest.Mode.auto) throw invalid("Automatic research is not supported yet; choose explicit web research");
    }
    private static AiGatewayException invalid(String message) {
        return new AiGatewayException(HttpStatus.BAD_REQUEST, "AI_RESEARCH_INVALID", message, false);
    }
}
