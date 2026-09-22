package in.voltforge.api.ai.observability;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiTelemetryTest {

    @Test
    void recordsOnlyBoundedOperationalFacts() {
        AiTelemetry telemetry = new AiTelemetry();
        String sentinel = "VFAI031_PRIVATE_PROMPT_AND_PROJECT_CONTENT";

        AiTelemetry.Observation stream = telemetry.start("chat.stream");
        telemetry.observeStreamEvent(stream, "start", 100);
        telemetry.observeStreamEvent(stream, "delta", 120);
        telemetry.finish(stream, "success");
        telemetry.recordAdmissionRejected(sentinel);

        Map<String, Object> snapshot = telemetry.snapshot();
        assertThat(snapshot.toString()).doesNotContain(sentinel);
        assertThat(snapshot).containsKeys("requests", "streams", "capacity", "alerts");
        assertThat(((Map<?, ?>) snapshot.get("streams")).get("active")).isEqualTo(0);
        assertThat(((Map<?, ?>) snapshot.get("capacity")).get("admissionRejections").toString())
                .contains("other");
        assertThat(snapshot.get("rawPromptStored")).isEqualTo(false);
        assertThat(snapshot.get("rawProjectContentStored")).isEqualTo(false);
        assertThat(snapshot.get("rawModelOutputStored")).isEqualTo(false);
    }
}
