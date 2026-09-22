package in.voltforge.api.ai.gateway;

import in.voltforge.api.config.VoltforgeAiConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestAdmissionTest {

    @Test
    void limitsAUserAndReleasesCapacityExactlyOnce() {
        VoltforgeAiConfig config = new VoltforgeAiConfig();
        ReflectionTestUtils.setField(config, "maxConcurrentStreamsPerUser", 1);
        ReflectionTestUtils.setField(config, "maxConcurrentStreamsPerProject", 1);
        AiRequestAdmission admission = new AiRequestAdmission(config);

        AiRequestAdmission.Lease lease = admission.acquireStreaming("user-027", "project-027");
        assertThat(admission.activeStreamsForUser("user-027")).isEqualTo(1);
        assertThatThrownBy(() -> admission.acquireStreaming("user-027", "project-028"))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(error -> assertThat(((AiGatewayException) error).getErrorCode())
                        .isEqualTo("AI_USER_STREAM_LIMIT"));

        lease.close();
        lease.close();
        assertThat(admission.activeStreamsForUser("user-027")).isZero();
    }

    @Test
    void limitsSharedProjectStreamsAcrossUsers() {
        VoltforgeAiConfig config = new VoltforgeAiConfig();
        ReflectionTestUtils.setField(config, "maxConcurrentStreamsPerUser", 2);
        ReflectionTestUtils.setField(config, "maxConcurrentStreamsPerProject", 1);
        AiRequestAdmission admission = new AiRequestAdmission(config);

        AiRequestAdmission.Lease lease = admission.acquireStreaming("user-a", "project-shared");
        assertThatThrownBy(() -> admission.acquireStreaming("user-b", "project-shared"))
                .isInstanceOf(AiGatewayException.class)
                .satisfies(error -> assertThat(((AiGatewayException) error).getErrorCode())
                        .isEqualTo("AI_PROJECT_STREAM_LIMIT"));
        lease.close();
        assertThat(admission.activeStreamsForUser("user-b")).isZero();
    }
}
