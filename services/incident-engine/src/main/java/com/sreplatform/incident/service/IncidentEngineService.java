package com.sreplatform.incident.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sreplatform.incident.config.MonitoredServices;
import com.sreplatform.incident.config.ServiceTarget;
import com.sreplatform.incident.model.Incident;
import com.sreplatform.incident.model.ServiceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Polls monitored services, detects degradation with debounce, opens incidents,
 * and maps symptoms to root causes and recommended actions (AWS IR style).
 */
@Service
public class IncidentEngineService {

    private static final Logger log = LoggerFactory.getLogger(IncidentEngineService.class);

    private final MonitoredServices monitoredServices;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final Map<String, Incident> incidents = new ConcurrentHashMap<>();
    private final List<Incident> history = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<String, Integer> degradedStreak = new ConcurrentHashMap<>();
    private final Map<String, Integer> healthyStreak = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    private final int detectAfterPolls;
    private final int clearAfterPolls;
    private final double errorRateThreshold;
    private final double latencyThresholdMs;
    private final String metricsSource;
    private final String tracesSource;

    public IncidentEngineService(
            MonitoredServices monitoredServices,
            ObjectMapper objectMapper,
            @Value("${app.detect-after-polls:2}") int detectAfterPolls,
            @Value("${app.clear-after-polls:3}") int clearAfterPolls,
            @Value("${app.thresholds.error-rate:20.0}") double errorRateThreshold,
            @Value("${app.thresholds.latency-ms:1500.0}") double latencyThresholdMs,
            @Value("${app.observability.metrics-source}") String metricsSource,
            @Value("${app.observability.traces-source}") String tracesSource) {
        this.monitoredServices = monitoredServices;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
        this.detectAfterPolls = detectAfterPolls;
        this.clearAfterPolls = clearAfterPolls;
        this.errorRateThreshold = errorRateThreshold;
        this.latencyThresholdMs = latencyThresholdMs;
        this.metricsSource = metricsSource;
        this.tracesSource = tracesSource;
    }

    @Scheduled(fixedDelayString = "${app.poll-interval-ms:5000}")
    public void poll() {
        for (ServiceTarget target : monitoredServices.targets()) {
            ServiceSnapshot snapshot = fetch(target);
            evaluate(target, snapshot);
        }
    }

    // ------------------------------------------------------------------
    // Public API (used by IncidentController)
    // ------------------------------------------------------------------

    public List<Incident> allIncidents() {
        List<Incident> combined = new ArrayList<>(history);
        for (Incident current : incidents.values()) {
            if (current.status() != Incident.Status.RESOLVED && !history.contains(current)) {
                combined.add(current);
            }
        }
        combined.sort(Comparator.comparing(Incident::openedAt).reversed());
        return combined;
    }

    public List<Incident> activeIncidents() {
        return incidents.values().stream()
                .filter(i -> i.status() != Incident.Status.RESOLVED)
                .sorted(Comparator.comparing(Incident::openedAt).reversed())
                .toList();
    }

    public Optional<Incident> incident(String id) {
        return history.stream()
                .filter(i -> i.id().equalsIgnoreCase(id))
                .findFirst()
                .or(() -> incidents.values().stream()
                        .filter(i -> i.id().equalsIgnoreCase(id))
                        .findFirst());
    }

    /**
     * Ingests a Prometheus/Alertmanager webhook payload and opens an incident.
     * Returns empty if the payload contains no alerts or the service already
     * has an open incident.
     */
    public synchronized Optional<Incident> ingestAlert(JsonNode body) {
        JsonNode alerts = body.path("alerts");
        if (!alerts.isArray() || alerts.isEmpty()) {
            return Optional.empty();
        }
        JsonNode alert = alerts.get(0);
        String alertName = alert.path("labels").path("alertname").asText("Alert");
        String service = alert.path("labels").path("service").asText("unknown");
        boolean critical = "critical".equalsIgnoreCase(alert.path("labels").path("severity").asText(""));
        String summary = alert.path("annotations").path("summary").asText(alertName);
        String description = alert.path("annotations").path("description")
                .asText("Automatically opened from Prometheus alert " + alertName);

        Incident existing = incidents.get(service);
        if (existing != null && existing.status() != Incident.Status.RESOLVED) {
            return Optional.of(existing);
        }

        int replicas = monitoredServices.targets().stream()
                .filter(t -> t.name().equalsIgnoreCase(service))
                .findFirst()
                .map(ServiceTarget::replicas)
                .orElse(1);

        Incident incident = new Incident(
                "INC-" + sequence.incrementAndGet(),
                service,
                critical ? Incident.Severity.SEV_1 : Incident.Severity.SEV_2,
                Incident.Status.OPEN,
                "Prometheus alert (" + alertName + ")",
                summary,
                summary,
                description,
                100.0, 0, replicas, replicas, List.of(),
                Instant.now(), Instant.now(), null);
        incidents.put(service, incident);
        history.add(incident);
        log.warn("INCIDENT FROM ALERT WEBHOOK [{}] service={} severity={} alert={}",
                incident.id(), service, incident.severity().label(), alertName);
        return Optional.of(incident);
    }

    public Incident updateStatus(Incident incident, Incident.Status status) {
        Incident updated = incident.withStatus(status);
        incidents.put(incident.service(), updated);
        return updated;
    }

    public List<ServiceSnapshot> serviceStatus() {
        List<ServiceSnapshot> snapshots = new ArrayList<>();
        for (ServiceTarget target : monitoredServices.targets()) {
            snapshots.add(fetch(target));
        }
        return snapshots;
    }

    // ------------------------------------------------------------------
    // Detection
    // ------------------------------------------------------------------

    private ServiceSnapshot fetch(ServiceTarget target) {
        try {
            JsonNode metrics = restClient.get()
                    .uri(target.url() + "/metrics/self")
                    .retrieve()
                    .body(JsonNode.class);
            String health = fetchHealth(target);
            String status = resolveStatus(target, metrics, health);
            return new ServiceSnapshot(
                    target.name(),
                    nodeAsText(metrics, "version", target.version()),
                    health,
                    status,
                    metrics == null ? 0 : metrics.path("errorRate").asDouble(0),
                    metrics == null ? 0 : metrics.path("p95LatencyMs").asLong(0),
                    metrics == null ? 0 : metrics.path("requestCount").asLong(0),
                    nodeToList(metrics),
                    Instant.now());
        } catch (Exception e) {
            log.warn("Cannot reach {}: {}", target.name(), e.getMessage());
            return new ServiceSnapshot(target.name(), target.version(), "DOWN",
                    "UNREACHABLE", 100.0, 0, 0, List.of(), Instant.now());
        }
    }

    private String fetchHealth(ServiceTarget target) {
        try {
            return restClient.get()
                    .uri(target.url() + "/actuator/health/readiness")
                    .retrieve()
                    .body(JsonNode.class)
                    .path("status")
                    .asText("UNKNOWN");
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String resolveStatus(ServiceTarget target, JsonNode metrics, String health) {
        if ("DOWN".equals(health) || "UNKNOWN".equals(health)) {
            return "DEGRADED";
        }
        if (metrics == null) {
            return "DEGRADED";
        }
        double errorRate = metrics.path("errorRate").asDouble(0);
        long latency = metrics.path("p95LatencyMs").asLong(0);
        if (errorRate >= errorRateThreshold || latency >= latencyThresholdMs
                || !nodeToList(metrics).isEmpty()) {
            return "DEGRADED";
        }
        return "HEALTHY";
    }

    private void evaluate(ServiceTarget target, ServiceSnapshot snapshot) {
        if (snapshot.degraded() || "UNREACHABLE".equals(snapshot.status())) {
            healthyStreak.put(target.name(), 0);
            int streak = degradedStreak.merge(target.name(), 1, Integer::sum);
            if (streak >= detectAfterPolls) {
                openOrUpdate(target, snapshot);
            }
        } else {
            degradedStreak.put(target.name(), 0);
            int streak = healthyStreak.merge(target.name(), 1, Integer::sum);
            if (streak >= clearAfterPolls) {
                resolve(target, snapshot);
            }
        }
    }

    private synchronized void openOrUpdate(ServiceTarget target, ServiceSnapshot snapshot) {
        String name = target.name();
        Incident existing = incidents.get(name);
        Diagnosis diagnosis = diagnose(target, snapshot);
        int healthyPods = healthyPods(target, snapshot);

        if (existing == null || existing.status() == Incident.Status.RESOLVED) {
            Incident incident = new Incident(
                    "INC-" + sequence.incrementAndGet(),
                    name,
                    diagnosis.severity(),
                    Incident.Status.OPEN,
                    diagnosis.detectedBy(),
                    diagnosis.signal(),
                    diagnosis.rootCause(),
                    diagnosis.recommendedAction(),
                    snapshot.errorRate(),
                    snapshot.p95LatencyMs(),
                    healthyPods,
                    target.replicas(),
                    snapshot.activeFaults(),
                    Instant.now(), Instant.now(), null);
            incidents.put(name, incident);
            history.add(incident);
            log.warn("INCIDENT DETECTED [{}] service={} severity={} errorRate={}% latency={}ms",
                    incident.id(), name, diagnosis.severity().label(),
                    snapshot.errorRate(), snapshot.p95LatencyMs());
        } else {
            incidents.put(name, existing.withSnapshot(snapshot.errorRate(),
                    snapshot.p95LatencyMs(), healthyPods, snapshot.activeFaults(),
                    diagnosis.severity()));
        }
    }

    private void resolve(ServiceTarget target, ServiceSnapshot snapshot) {
        Incident existing = incidents.get(target.name());
        if (existing != null && existing.status() != Incident.Status.RESOLVED) {
            Incident resolved = existing.withStatus(Incident.Status.RESOLVED);
            incidents.put(target.name(), resolved);
            log.warn("INCIDENT RESOLVED [{}] service={}", existing.id(), target.name());
        }
    }

    // ------------------------------------------------------------------
    // Diagnosis
    // ------------------------------------------------------------------

    private Diagnosis diagnose(ServiceTarget target, ServiceSnapshot snapshot) {
        List<String> faults = snapshot.activeFaults();
        String version = snapshot.version();
        boolean critical = snapshot.errorRate() >= errorRateThreshold
                || "DOWN".equals(snapshot.health())
                || faults.contains("break-dependency")
                || faults.contains("expired-certificate");

        String rootCause;
        String action;
        String signal = "http_requests_total error rate";

        if (faults.contains("break-dependency")) {
            rootCause = "Downstream dependency orders-api unavailable — circuit breaker OPEN (deployment " + version + ")";
            action = "Rollback payments-api OR restore orders-api dependency first";
        } else if (faults.contains("expired-certificate")) {
            rootCause = "Expired upstream certificate — TLS handshake failures (deployment " + version + ")";
            action = "Rotate certificate then rollback deployment";
        } else if (faults.contains("http-500")) {
            rootCause = "Service returning HTTP 500s — bad release " + version;
            action = "Rollback deployment to last known good";
            signal = "http_server_requests_total 5xx";
        } else if (faults.contains("cpu-exhaustion")) {
            rootCause = "CPU saturation — request handling starved (deployment " + version + ")";
            action = "Scale out replicas / raise CPU limits (HPA)";
        } else if (faults.contains("inject-latency")) {
            rootCause = "Elevated p95 latency — queue/thread pool saturation (deployment " + version + ")";
            action = "Rollback OR scale out (HPA)";
            signal = "p95 latency";
        } else if ("DOWN".equals(snapshot.health())) {
            rootCause = "Readiness probe failing — deployment " + version;
            action = "Rollback deployment to last known good";
            signal = "health probe";
        } else if (snapshot.errorRate() >= errorRateThreshold) {
            rootCause = "Elevated error rate — deployment " + version;
            action = "Rollback deployment to last known good";
        } else {
            rootCause = "Degraded performance — deployment " + version;
            action = "Investigate; consider rollback";
            signal = "latency";
        }

        return new Diagnosis(
                critical ? Incident.Severity.SEV_1 : Incident.Severity.SEV_2,
                metricsSource + " + " + tracesSource,
                signal,
                rootCause,
                action);
    }

    private int healthyPods(ServiceTarget target, ServiceSnapshot snapshot) {
        if ("UNREACHABLE".equals(snapshot.status())) {
            return 1;
        }
        if (snapshot.healthy()) {
            return target.replicas();
        }
        int reduced = target.replicas() - Math.max(1, snapshot.activeFaults().size());
        return Math.max(1, reduced);
    }

    private record Diagnosis(Incident.Severity severity, String detectedBy, String signal,
                             String rootCause, String recommendedAction) {}

    private static String nodeAsText(JsonNode node, String field, String fallback) {
        return node == null ? fallback : node.path(field).asText(fallback);
    }

    private List<String> nodeToList(JsonNode node) {
        if (node == null || !node.path("activeFaults").isArray()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    node.path("activeFaults").toString(),
                    new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
