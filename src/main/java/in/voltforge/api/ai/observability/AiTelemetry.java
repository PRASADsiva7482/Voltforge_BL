package in.voltforge.api.ai.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/**
 * Content-free process-local telemetry for the Spring AI gateway.
 *
 * Only allowlisted operation/outcome/event labels and bounded timing samples
 * are retained. Request bodies, project identifiers, JWT subjects, response
 * text, and request IDs are deliberately not stored in this component.
 */
@Component("voltforgeAiTelemetry")
public class AiTelemetry implements HealthIndicator {

    public enum Stage { ADMISSION, DOCUMENT_RETRIEVAL, TURN_PREPARATION, PERSISTENCE }

    private static final int MAX_SAMPLES = 256;
    private static final long ALERT_WINDOW_NANOS = 300_000_000_000L;
    // >2x the measured 26.422s local capacity maximum, rounded up. Not a production SLO.
    private static final double SLOW_STREAM_MS = 60_000.0;
    private static final Set<String> OPERATIONS = Set.of(
            "chat.sync", "chat.stream", "generation", "review", "validation",
            "schematic", "drc", "gerber", "memory", "other");
    private static final Set<String> OUTCOMES = Set.of(
            "success", "failure", "cancelled", "rejected", "timeout", "other");
    private static final Set<String> STREAM_EVENT_TYPES = Set.of(
            "start", "status", "keepalive", "sources", "tool", "citation", "uncertainty",
            "token", "delta", "proposal", "complete", "done", "error");
    private static final Set<String> ADMISSION_CODES = Set.of(
            "AI_AUTHENTICATION_REQUIRED", "AI_USER_STREAM_LIMIT", "AI_PROJECT_STREAM_LIMIT");

    private final ConcurrentMap<String, LongAdder> startedByOperation = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> completedByOutcome = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> admissionRejections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> streamEvents = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LatencyWindow> latencyByOperation = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LatencyWindow> stageLatency = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> stageOutcomes = new ConcurrentHashMap<>();
    private final LatencyWindow streamLatency = new LatencyWindow();
    private final LatencyWindow firstTokenLatency = new LatencyWindow();
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final AtomicInteger activeStreams = new AtomicInteger();
    private final AtomicLong peakActiveRequests = new AtomicLong();
    private final AtomicLong peakActiveStreams = new AtomicLong();
    private final AtomicLong lastCapacityRejection = new AtomicLong(Long.MIN_VALUE);
    private final ConcurrentMap<String, Long> recentFaults = new ConcurrentHashMap<>();
    private final Deque<StreamResult> recentStreamResults = new ArrayDeque<>();
    private final LongSupplier clock;

    public AiTelemetry() { this(System::nanoTime); }

    AiTelemetry(LongSupplier clock) { this.clock = clock; }

    /** Measures a synchronous owner boundary, including exceptional exits. No input/result is retained. */
    public <T> T measure(Stage stage, Supplier<T> action) {
        long started = clock.getAsLong();
        String outcome = "failure";
        try {
            T result = action.get();
            outcome = "success";
            return result;
        } finally {
            String label = stage.name().toLowerCase(java.util.Locale.ROOT);
            stageLatency.computeIfAbsent(label, ignored -> new LatencyWindow()).add(elapsedMs(started));
            stageOutcomes.computeIfAbsent(label + "." + outcome, ignored -> new LongAdder()).increment();
            if (stage == Stage.PERSISTENCE && "failure".equals(outcome)) recordFaultCode("PERSISTENCE_FAILED");
        }
    }

    public void recordFaultCode(String code) {
        String signal = switch (code == null ? "" : code) {
            case "AI_MODEL_UNAVAILABLE", "AI_SERVICE_UNAVAILABLE" -> "modelUnavailable";
            case "AI_WEB_UNAVAILABLE", "WEB_DISABLED", "SEARCH_PROVIDER_UNAVAILABLE" -> "searchUnavailable";
            case "PERSISTENCE_FAILED" -> "persistenceFailure";
            default -> null;
        };
        if (signal != null) recentFaults.merge(signal, clock.getAsLong(), Math::max);
    }

    public Observation start(String operation) {
        String safeOperation = operation(operation);
        startedByOperation.computeIfAbsent(safeOperation, ignored -> new LongAdder()).increment();
        int active = activeRequests.incrementAndGet();
        peakActiveRequests.accumulateAndGet(active, Math::max);
        if ("chat.stream".equals(safeOperation)) {
            int streams = activeStreams.incrementAndGet();
            peakActiveStreams.accumulateAndGet(streams, Math::max);
        }
        return new Observation(safeOperation, clock.getAsLong());
    }

    public void finish(Observation observation, String outcome) {
        if (observation == null || !observation.finished.compareAndSet(false, true)) {
            return;
        }
        String safeOutcome = outcome != null && OUTCOMES.contains(outcome) ? outcome : "other";
        if (observation.streamError.get() && ("success".equals(safeOutcome) || "cancelled".equals(safeOutcome))) safeOutcome = "failure";
        else if (observation.streamComplete.get() && "cancelled".equals(safeOutcome)) safeOutcome = "success";
        completedByOutcome.computeIfAbsent(safeOutcome, ignored -> new LongAdder()).increment();
        activeRequests.updateAndGet(value -> Math.max(0, value - 1));
        if ("chat.stream".equals(observation.operation)) {
            recordStreamResult(safeOutcome);
            activeStreams.updateAndGet(value -> Math.max(0, value - 1));
            double duration = elapsedMs(observation.startedNanos);
            streamLatency.add(duration);
            if (duration >= SLOW_STREAM_MS) recentFaults.merge("slowStream", clock.getAsLong(), Math::max);
            if (observation.firstTokenNanos.get() != Long.MIN_VALUE) {
                firstTokenLatency.add((observation.firstTokenNanos.get() - observation.startedNanos) / 1_000_000.0);
            }
        } else {
            latencyByOperation.computeIfAbsent(observation.operation, ignored -> new LatencyWindow())
                    .add(elapsedMs(observation.startedNanos));
        }
    }

    public void observeStreamEvent(Observation observation, String eventType, int payloadBytes) {
        if (observation == null || observation.finished.get()) {
            return;
        }
        String safeEvent = eventType != null && STREAM_EVENT_TYPES.contains(eventType) ? eventType : "other";
        if ("error".equals(safeEvent)) observation.streamError.set(true);
        if ("complete".equals(safeEvent) || "done".equals(safeEvent)) observation.streamComplete.set(true);
        streamEvents.computeIfAbsent(safeEvent, ignored -> new LongAdder()).increment();
        observation.eventCount.incrementAndGet();
        observation.eventBytes.addAndGet(Math.max(0, Math.min(payloadBytes, 262_144)));
        if (payloadBytes > 0 && ("delta".equals(safeEvent) || "token".equals(safeEvent))) {
            observation.firstTokenNanos.compareAndSet(Long.MIN_VALUE, clock.getAsLong());
        }
    }

    public void recordAdmissionRejected(String code) {
        admissionRejections.computeIfAbsent(safeCode(code), ignored -> new LongAdder()).increment();
        if ("AI_USER_STREAM_LIMIT".equals(code) || "AI_PROJECT_STREAM_LIMIT".equals(code)) {
            lastCapacityRejection.accumulateAndGet(clock.getAsLong(), Math::max);
        }
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", "vfai031-spring-telemetry-v1");
        result.put("requests", Map.of(
                "active", activeRequests.get(),
                "peakActive", peakActiveRequests.get(),
                "startedByOperation", sortedCounts(startedByOperation),
                "completedByOutcome", sortedCounts(completedByOutcome),
                "latencyMs", sortedLatency(latencyByOperation)));
        result.put("streams", Map.of(
                "active", activeStreams.get(),
                "peakActive", peakActiveStreams.get(),
                "events", sortedCounts(streamEvents),
                "latencyMs", streamLatency.snapshot(),
                "firstTokenLatencyMs", firstTokenLatency.snapshot()));
        result.put("capacity", Map.of(
                "admissionRejections", sortedCounts(admissionRejections),
                "activeRequests", activeRequests.get(),
                "activeStreams", activeStreams.get()));
        result.put("stages", Map.of("latencyMs", sortedLatency(stageLatency),
                "outcomes", sortedCounts(stageOutcomes)));
        long lastRejection = lastCapacityRejection.get();
        long age = clock.getAsLong() - lastRejection;
        boolean admissionSaturation = lastRejection != Long.MIN_VALUE && age >= 0 && age < ALERT_WINDOW_NANOS;
        Map<String, Boolean> states = new TreeMap<>();
        states.put("admissionSaturation", admissionSaturation);
        long now = clock.getAsLong();
        for (String signal : List.of("modelUnavailable", "searchUnavailable", "persistenceFailure", "slowStream")) {
            Long last = recentFaults.get(signal);
            states.put(signal, last != null && now - last >= 0 && now - last < ALERT_WINDOW_NANOS);
        }
        var errorRate = streamErrorRate();
        states.put("streamErrorRate", (Boolean) errorRate.get("active"));
        result.put("streamErrorRate", errorRate);
        result.put("alerts", Map.of(
                "active", states.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList(),
                "state", states,
                "windowSeconds", 300, "slowStreamThresholdMs", SLOW_STREAM_MS));
        result.put("rawPromptStored", false);
        result.put("rawProjectContentStored", false);
        result.put("rawModelOutputStored", false);
        return result;
    }

    private record StreamResult(long at, boolean failed) {}

    private synchronized void recordStreamResult(String outcome) {
        if (!Set.of("success", "failure", "timeout").contains(outcome)) return;
        expireStreamResults();
        if (recentStreamResults.size() == MAX_SAMPLES) recentStreamResults.removeFirst();
        recentStreamResults.addLast(new StreamResult(clock.getAsLong(), !"success".equals(outcome)));
    }

    private void expireStreamResults() {
        long now = clock.getAsLong();
        while (!recentStreamResults.isEmpty() && now - recentStreamResults.getFirst().at() >= ALERT_WINDOW_NANOS)
            recentStreamResults.removeFirst();
    }

    private synchronized Map<String, Object> streamErrorRate() {
        expireStreamResults();
        int count = recentStreamResults.size();
        long failures = recentStreamResults.stream().filter(StreamResult::failed).count();
        double rate = count == 0 ? 0 : (double) failures / count;
        return Map.of("samples", count, "failures", failures, "rate", rate,
            "minimumSamples", 5, "threshold", 0.20, "windowSeconds", 300,
            "active", count >= 5 && rate >= 0.20);
    }

    @Override
    public Health health() {
        return Health.up().withDetails(snapshot()).build();
    }

    private String operation(String value) {
        return value != null && OPERATIONS.contains(value) ? value : "other";
    }

    private String safeCode(String value) {
        return value != null && ADMISSION_CODES.contains(value) ? value : "other";
    }

    private double elapsedMs(long startedNanos) {
        return Math.max(0.0, Math.min(600_000.0, (clock.getAsLong() - startedNanos) / 1_000_000.0));
    }

    private Map<String, Long> sortedCounts(ConcurrentMap<String, LongAdder> values) {
        Map<String, Long> result = new TreeMap<>();
        values.forEach((key, value) -> result.put(key, value.sum()));
        return result;
    }

    private Map<String, Object> sortedLatency(ConcurrentMap<String, LatencyWindow> values) {
        Map<String, Object> result = new TreeMap<>();
        values.forEach((key, value) -> result.put(key, value.snapshot()));
        return result;
    }

    public static final class Observation {
        private final String operation;
        private final long startedNanos;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean streamError = new AtomicBoolean();
        private final AtomicBoolean streamComplete = new AtomicBoolean();
        private final AtomicLong firstTokenNanos = new AtomicLong(Long.MIN_VALUE);
        private final AtomicLong eventCount = new AtomicLong();
        private final AtomicLong eventBytes = new AtomicLong();

        private Observation(String operation, long startedNanos) {
            this.operation = operation;
            this.startedNanos = startedNanos;
        }
    }

    private static final class LatencyWindow {
        private final Deque<Double> values = new ArrayDeque<>();

        private synchronized void add(double value) {
            if (values.size() >= MAX_SAMPLES) {
                values.removeFirst();
            }
            values.addLast(Math.max(0.0, Math.min(600_000.0, value)));
        }

        private synchronized Map<String, Object> snapshot() {
            if (values.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("count", 0);
                empty.put("sumMs", 0.0);
                empty.put("p50Ms", null);
                empty.put("p95Ms", null);
                empty.put("maxMs", null);
                return empty;
            }
            List<Double> sorted = new ArrayList<>(values);
            Collections.sort(sorted);
            return Map.of(
                    "count", sorted.size(),
                    "sumMs", round(sorted.stream().mapToDouble(Double::doubleValue).sum()),
                    "p50Ms", round(sorted.get((int) ((sorted.size() - 1) * 0.50))),
                    "p95Ms", round(sorted.get((int) ((sorted.size() - 1) * 0.95))),
                    "maxMs", round(sorted.get(sorted.size() - 1)));
        }

        private double round(double value) {
            return Math.round(value * 1000.0) / 1000.0;
        }
    }
}
