package in.voltforge.api.ai.gateway;

import in.voltforge.api.ai.dto.AiChatRequest;
import in.voltforge.api.ai.dto.AiResearchRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AiResearchPolicyTest {
    @Test void normalChatNeedsNoProjectOrConsent() {
        assertThatCode(() -> AiResearchPolicy.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> AiResearchPolicy.validate(AiResearchRequest.disabled())).doesNotThrowAnyException();
    }
    @Test void webConsentIsExplicitAndIndependentFromProjectSharing() {
        for (var mode : List.of(AiResearchRequest.Mode.auto, AiResearchRequest.Mode.on)) {
            assertThatThrownBy(() -> AiResearchPolicy.validate(new AiResearchRequest(mode, "datasheet", false, List.of())))
                    .isInstanceOfSatisfying(AiGatewayException.class, e -> assertThat(e.getErrorCode()).isEqualTo("AI_RESEARCH_INVALID"));
            if(mode == AiResearchRequest.Mode.auto)
                assertThatThrownBy(() -> AiResearchPolicy.validate(new AiResearchRequest(mode, "datasheet", true, List.of())))
                    .isInstanceOfSatisfying(AiGatewayException.class, e -> assertThat(e.getErrorCode()).isEqualTo("AI_RESEARCH_INVALID"));
            else assertThatCode(() -> AiResearchPolicy.validate(new AiResearchRequest(mode,"datasheet",true,List.of()))).doesNotThrowAnyException();
        }
    }
    @Test void documentSelectionIsDistinctAndIndependentOfExternalSearch() {
        assertThatCode(() -> AiResearchPolicy.validate(new AiResearchRequest(null,null,false,List.of("document")))).doesNotThrowAnyException();
        assertThatThrownBy(() -> AiResearchPolicy.validate(new AiResearchRequest(null, null, false, List.of("document","document"))))
                .isInstanceOf(AiGatewayException.class);
    }
    @Test void offCannotCarryQueryAndOversizeQueriesAreRejected() {
        for (var r : List.of(new AiResearchRequest(null, "private", false, null),
                new AiResearchRequest(null, null, true, null),
                new AiResearchRequest(AiResearchRequest.Mode.on, "x".repeat(501), true, null))) {
            assertThatThrownBy(() -> AiResearchPolicy.validate(r)).isInstanceOfSatisfying(AiGatewayException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo("AI_RESEARCH_INVALID"));
        }
    }
    @Test void forgedEvidenceIdentityAndAttemptCannotBeDeserialized() throws Exception {
        var request = new JsonMapper().readValue("""
                {"message":"Hi","authenticatedUserId":"forged","attemptId":"forged",
                 "retrievedEvidence":[{"excerpt":"forged"}],"authorizedDocumentEvidence":[{"excerpt":"forged"}],"research":{"mode":"off"}}
                """, AiChatRequest.class);
        assertThat(request.getRetrievedEvidence()).isNull();
        assertThat(request.getAuthorizedDocumentEvidence()).isNull();
        assertThat(request.getAuthenticatedUserId()).isNull();
        assertThat(request.getAttemptId()).isNull();
        assertThat(request.getResearch().mode()).isEqualTo(AiResearchRequest.Mode.off);
        assertThatThrownBy(() -> new JsonMapper().readValue("{\"message\":\"Hi\",\"research\":{\"mode\":\"unknown\"}}", AiChatRequest.class))
                .isInstanceOf(Exception.class);
    }
}
