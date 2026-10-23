package in.voltforge.api.ai.dto;

import java.util.List;
import jakarta.validation.constraints.Size;

/** Consent to send only the reviewed publicQuery, never implicit project/chat context. */
public record AiResearchRequest(
        Mode mode,
        @Size(max = 500) String publicQuery,
        Boolean consentToExternalQuery,
        @Size(max = 10) List<@Size(min = 1, max = 80) String> documentIds) {
    public enum Mode { off, auto, on }
    public AiResearchRequest {
        mode = mode == null ? Mode.off : mode;
        consentToExternalQuery = Boolean.TRUE.equals(consentToExternalQuery);
        publicQuery = publicQuery == null ? null : publicQuery.strip();
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
    }
    public static AiResearchRequest disabled() { return new AiResearchRequest(Mode.off, null, false, List.of()); }
    @Override public String toString() { return "AiResearchRequest[query omitted]"; }
}
