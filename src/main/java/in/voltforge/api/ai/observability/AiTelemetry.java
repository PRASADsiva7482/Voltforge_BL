package in.voltforge.api.ai.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
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

/**
 * Content-free process-local telemetry for the Spring AI gateway.
 *
 * Only allowlisted operation/outcome/event labels and bounded timing samples
 * are retained. Request bodies, project identifiers, JWT subjects, response
 * text, and request IDs are deliberately not stored in this component.
 */
@Component("voltforgeAiTelemetry")
public class AiTelemetry implements HealthIndicator {

    private static final int MAX_SAMPLES = 256;
    private static final Set<String> OPERATIONS = Set.of(
            "chat.sync", "chat.stream", "generation", "review", "validation",
            "schematic", "drc", "gerber", "memory", "other");
    private static final Set<String> OUTCOMES = Set.of(
            "success", "failure", "cancelled", "rejected", "timeout", "other");
    private static final Set<String> STREAM_EVENT_TYPES = Set.of(
            "start", "tool", "citation", "uncertainty", "delta", "proposal", "complete", "error");

    private final ConcurrentMap<String, LongAdder> startedByOperation = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> completedByOutcome = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> admissionRejections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LongAdder> streamEvents = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LatencyWindow> latencyByOperation = new ConcurrentHashMap<>();
    private final LatencyWindow streamLatency = new LatencyWindow();
    private final LatencyWindow firstTokenLatency = new LatencyWindow();
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final AtomicInteger activeStreams = new AtomicInteger();
    private final AtomicLong peakActiveRequests = new AtomicLong();
    private final AtomicLong peakActiveStreams = new AtomicLong();

    public Observation start(String operation) {
        String safeOperation = operation(operation);
        startedByOperation.computeIfAbsent(safeOperation, ignored -> new LongAdder()).increment();
        int active = activeRequests.incrementAndGet();
        peakActiveRequests.accumulateAndGet(active, Math::max);
        if ("chat.stream".equals(safeOperation)) {
            int streams = activeStreams.incrementAndGet();
            peakActiveStreams.accumulateAndGet(streams, Math::max);
        }
        return new Observation(safeOperation, System.nanoTime());
    }

    public void finish(Observation observation, String outcome) {
        if (observation == null || !observation.finished.compareAndSet(false, true)) {
            return;
        }
        String safeOutcome = OUTCOMES.contains(outcome) ? outcome : "other";
        completedByOutcome.computeIfAbsent(safeOutcome, ignored -> new LongAdder()).increment();
        activeRequests.updateAndGet(value -> Math.max(0, value - 1));
        if ("chat.stream".equals(observation.operation)) {
            activeStreams.updateAndGet(value -> Math.max(0, value - 1));
            streamLatency.add(elapsedMs(observation.startedNanos));
            if (observation.firstTokenNanos.get() > 0) {
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
        streamEvents.computeIfAbsent(safeEvent, ignored -> new LongAdder()).increment();
        observation.eventCount.incrementAndGet();
        observation.eventBytes.addAndGet(Math.max(0, Math.min(payloadBytes, 262_144)));
        if ("delta".equals(safeEvent)) {
            observation.firstTokenNanos.compareAndSet(0, System.nanoTime());
        }
    }

    public void recordAdmissionRejected(String code) {
        admissionRejections.computeIfAbsent(safeCode(code), ignored -> new LongAdder()).increment();
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
        boolean admissionSaturation = !admissionRejections.isEmpty();
        result.put("alerts", Map.of(
                "active", admissionSaturation ? List.of("admissionSaturation") : List.of(),
                "state", Map.of("admissionSaturation", admissionSaturation)));
        result.put("rawPromptStored", false);
        result.put("rawProjectContentStored", false);
        result.put("rawModelOutputStored", false);
        return result;
    }

    @Override
    public Health health() {
        return Health.up().withDetails(snapshot()).build();
    }

    private String operation(String value) {
        return value != null && OPERATIONS.contains(value) ? value : "other";
    }

    private String safeCode(String value) {
        if (value == null || !value.matches("^(AI_|API_|CLIENT_|INTERNET_|LOCAL_|MEMORY_|MODEL_|PROVIDER_|QG_|REQUEST_|UNSAFE_)[A-Z0-9_-]{0,63}$")) {
            return "other";
        }
        return value;
    }

    private double elapsedMs(long startedNanos) {
        return Math.max(0.0, Math.min(600_000.0, (System.nanoTime() - startedNanos) / 1_000_000.0));
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
        private final AtomicLong firstTokenNanos = new AtomicLong();
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
