package in.voltforge.api.ai.observability;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiTelemetryTest {
    @Test void slowStreamWarningUsesMeasuredLocalBudgetAndExpires() {
        var clock = new java.util.concurrent.atomic.AtomicLong(0);
        var telemetry = new AiTelemetry(clock::get);
        var normal = telemetry.start("chat.stream");
        telemetry.observeStreamEvent(normal,"token",1);
        clock.set(26_422_000_000L); telemetry.finish(normal,"success");
        assertThat(((Map<?, ?>)((Map<?, ?>)telemetry.snapshot().get("alerts")).get("state")).get("slowStream")).isEqualTo(false);
        var slow = telemetry.start("chat.stream");
        clock.addAndGet(60_000_000_000L); telemetry.finish(slow,"success");
        assertThat(((Map<?, ?>)((Map<?, ?>)telemetry.snapshot().get("alerts")).get("state")).get("slowStream")).isEqualTo(true);
        clock.addAndGet(300_000_000_000L);
        assertThat(((Map<?, ?>)((Map<?, ?>)telemetry.snapshot().get("alerts")).get("state")).get("slowStream")).isEqualTo(false);
        assertThat(((Map<?, ?>)((Map<?, ?>)telemetry.snapshot().get("streams")).get("firstTokenLatencyMs")).get("count")).isEqualTo(1);
    }
    @Test void streamFailureRateHasMinimumSampleExpiryAndBoundedStorage() {
        var clock = new java.util.concurrent.atomic.AtomicLong(0);
        var telemetry = new AiTelemetry(clock::get);
        for(int i=0;i<4;i++) telemetry.finish(telemetry.start("chat.stream"),"success");
        telemetry.finish(telemetry.start("chat.stream"),"cancelled");
        assertThat(((Map<?, ?>) telemetry.snapshot().get("streamErrorRate")).get("active")).isEqualTo(false);
        var error = telemetry.start("chat.stream");
        telemetry.observeStreamEvent(error,"error",10);
        telemetry.finish(error,"success"); telemetry.finish(error,"failure");
        var rate = (Map<?, ?>) telemetry.snapshot().get("streamErrorRate");
        assertThat(rate.get("samples")).isEqualTo(5);
        assertThat(rate.get("failures")).isEqualTo(1L);
        assertThat(rate.get("active")).isEqualTo(true);
        for(int i=0;i<1000;i++) telemetry.finish(telemetry.start("chat.stream"),"success");
        rate = (Map<?, ?>) telemetry.snapshot().get("streamErrorRate");
        assertThat(rate.get("samples")).isEqualTo(256);
        assertThat(rate.get("active")).isEqualTo(false);
        clock.set(300_000_000_000L);
        assertThat(((Map<?, ?>) telemetry.snapshot().get("streamErrorRate")).get("samples")).isEqualTo(0);
    }
    @Test void downstreamTerminalCancellationPreservesObservedProviderOutcome() {
        var telemetry = new AiTelemetry();
        for(String type : new String[]{"complete", "error"}) {
            var stream = telemetry.start("chat.stream");
            telemetry.observeStreamEvent(stream,type,20);
            telemetry.finish(stream,"cancelled");
        }
        var counts = (Map<?, ?>) ((Map<?, ?>) telemetry.snapshot().get("requests")).get("completedByOutcome");
        assertThat(counts.get("success")).isEqualTo(1L);
        assertThat(counts.get("failure")).isEqualTo(1L);
        assertThat(((Map<?, ?>) telemetry.snapshot().get("streamErrorRate")).get("samples")).isEqualTo(2);
    }

    @Test
    void knownFaultsAreBoundedExpireAndPreservePersistenceFailure() {
        var clock = new java.util.concurrent.atomic.AtomicLong(0);
        var telemetry = new AiTelemetry(clock::get);
        telemetry.recordFaultCode("AI_MODEL_UNAVAILABLE");
        telemetry.recordFaultCode("AI_WEB_UNAVAILABLE");
        for (int i = 0; i < 1000; i++) telemetry.recordFaultCode("PRIVATE_FAILURE_" + i);
        var failure = new IllegalStateException("private database exception");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> telemetry.measure(AiTelemetry.Stage.PERSISTENCE,
            () -> { throw failure; })).isSameAs(failure);
        var alerts = (Map<?, ?>) telemetry.snapshot().get("alerts");
        var states = (Map<?, ?>) alerts.get("state");
        for (String signal : new String[]{"modelUnavailable", "searchUnavailable", "persistenceFailure"}) {
            assertThat(states.get(signal)).isEqualTo(true);
        }
        assertThat(states.size()).isEqualTo(6);
        assertThat(telemetry.snapshot().toString()).doesNotContain("PRIVATE_FAILURE", failure.getMessage());
        clock.set(300_000_000_000L);
        assertThat((java.util.List<?>) ((Map<?, ?>) telemetry.snapshot().get("alerts")).get("active")).isEmpty();
    }

    @Test
    void capacityAlertExpiresAndDoesNotConfuseAuthenticationWithSaturation() {
        var clock = new java.util.concurrent.atomic.AtomicLong(0);
        var telemetry = new AiTelemetry(clock::get);
        telemetry.recordAdmissionRejected("AI_AUTHENTICATION_REQUIRED");
        telemetry.recordAdmissionRejected("AI_PRIVATE_USER_123");
        assertThat(capacityAlert(telemetry)).isFalse();
        telemetry.recordAdmissionRejected("AI_USER_STREAM_LIMIT");
        assertThat(capacityAlert(telemetry)).isTrue();
        clock.set(299_999_999_999L);
        assertThat(capacityAlert(telemetry)).isTrue();
        clock.set(300_000_000_000L);
        assertThat(capacityAlert(telemetry)).isFalse();
        telemetry.recordAdmissionRejected("AI_PROJECT_STREAM_LIMIT");
        assertThat(capacityAlert(telemetry)).isTrue();
        clock.set(600_000_000_000L);
        assertThat(capacityAlert(telemetry)).isFalse();
    }

    private boolean capacityAlert(AiTelemetry telemetry) {
        var alerts = (Map<?, ?>) telemetry.snapshot().get("alerts");
        return (Boolean) ((Map<?, ?>) alerts.get("state")).get("admissionSaturation");
    }

    @Test
    void stageTimingsAreBoundedAndRetainNeitherResultsNorFailures() {
        AiTelemetry telemetry = new AiTelemetry();
        String privateValue = "private stage result";
        for (int index = 0; index < 1000; index++) {
            assertThat(telemetry.measure(AiTelemetry.Stage.DOCUMENT_RETRIEVAL, () -> privateValue))
                    .isSameAs(privateValue);
        }
        RuntimeException failure = new IllegalStateException("private database details");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> telemetry.measure(
                AiTelemetry.Stage.PERSISTENCE, () -> { throw failure; })).isSameAs(failure);
        var stages = (Map<?, ?>) telemetry.snapshot().get("stages");
        var latency = (Map<?, ?>) stages.get("latencyMs");
        assertThat(((Map<?, ?>) latency.get("document_retrieval")).get("count")).isEqualTo(256);
        assertThat(((Map<?, ?>) latency.get("persistence")).get("count")).isEqualTo(1);
        assertThat(((Map<?, ?>) stages.get("outcomes")).get("document_retrieval.success")).isEqualTo(1000L);
        assertThat(((Map<?, ?>) stages.get("outcomes")).get("persistence.failure")).isEqualTo(1L);
        assertThat(stages.toString()).doesNotContain(privateValue, failure.getMessage());
    }

    @Test
    void tokenAndLegacyDeltaAreTimedButStatusTrafficIsNot() {
        AiTelemetry telemetry = new AiTelemetry();
        var statusOnly = telemetry.start("chat.stream");
        for (String event : new String[]{"start", "status", "keepalive", "sources", "complete", "unknown"}) {
            telemetry.observeStreamEvent(statusOnly, event, 50);
        }
        telemetry.observeStreamEvent(statusOnly, "token", 0);
        telemetry.finish(statusOnly, "success");
        assertThat(firstTokenCount(telemetry)).isZero();
        for (String event : new String[]{"token", "delta"}) {
            var stream = telemetry.start("chat.stream");
            telemetry.observeStreamEvent(stream, event, 50);
            telemetry.observeStreamEvent(stream, event, 50);
            telemetry.finish(stream, "success");
            telemetry.finish(stream, "failure");
            telemetry.observeStreamEvent(stream, "token", 50);
        }
        assertThat(firstTokenCount(telemetry)).isEqualTo(2);
        assertThat(((Map<?, ?>) telemetry.snapshot().get("streams")).get("active")).isEqualTo(0);
    }

    @Test
    void arbitraryLabelsCannotGrowCardinalityOrLeakPrefixedIdentifiers() {
        AiTelemetry telemetry = new AiTelemetry();
        for (int index = 0; index < 1000; index++) {
            var stream = telemetry.start("private-operation-" + index);
            telemetry.observeStreamEvent(stream, "private-event-" + index, Integer.MAX_VALUE);
            telemetry.recordAdmissionRejected("AI_PRIVATE_USER_" + index);
            telemetry.finish(stream, null);
        }
        telemetry.recordAdmissionRejected("AI_USER_STREAM_LIMIT");
        var snapshot = telemetry.snapshot();
        assertThat(snapshot.toString()).doesNotContain("PRIVATE_USER", "private-operation", "private-event");
        var counts = (Map<?, ?>) ((Map<?, ?>) snapshot.get("capacity")).get("admissionRejections");
        assertThat(counts.size()).isEqualTo(2);
        assertThat(counts.get("other")).isEqualTo(1000L);
        assertThat(counts.get("AI_USER_STREAM_LIMIT")).isEqualTo(1L);
    }

    @Test
    void timingWindowsRetainOnlyLatest256Samples() {
        AiTelemetry telemetry = new AiTelemetry();
        for (int index = 0; index < 1000; index++) {
            var stream = telemetry.start("chat.stream");
            telemetry.observeStreamEvent(stream, "token", 50);
            telemetry.finish(stream, "success");
        }
        assertThat(firstTokenCount(telemetry)).isEqualTo(256);
        var streams = (Map<?, ?>) telemetry.snapshot().get("streams");
        assertThat(((Map<?, ?>) streams.get("latencyMs")).get("count")).isEqualTo(256);
    }

    private int firstTokenCount(AiTelemetry telemetry) {
        var streams = (Map<?, ?>) telemetry.snapshot().get("streams");
        return (Integer) ((Map<?, ?>) streams.get("firstTokenLatencyMs")).get("count");
    }

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
