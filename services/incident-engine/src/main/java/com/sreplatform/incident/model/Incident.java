package com.sreplatform.incident.model;

import java.time.Instant;
import java.util.List;

public record Incident(
        String id,
        String service,
        Severity severity,
        Status status,
        String detectedBy,
        String signal,
        String rootCause,
        String recommendedAction,
        double errorRate,
        long p95LatencyMs,
        int healthyPods,
        int replicas,
        List<String> activeFaults,
        Instant openedAt,
        Instant updatedAt,
        Instant resolvedAt) {

    public enum Severity {
        SEV_1("SEV-1"), SEV_2("SEV-2");

        private final String label;

        Severity(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Status {
        OPEN, ACK, RESOLVED
    }

    public Incident withStatus(Status newStatus) {
        return new Incident(id, service, severity, newStatus, detectedBy, signal,
                rootCause, recommendedAction, errorRate, p95LatencyMs, healthyPods,
                replicas, activeFaults, openedAt, Instant.now(),
                newStatus == Status.RESOLVED ? Instant.now() : resolvedAt);
    }

    public Incident withSnapshot(double newErrorRate, long newLatency, int newHealthyPods,
                                 List<String> newFaults, Severity newSeverity) {
        return new Incident(id, service, newSeverity, status, detectedBy, signal,
                rootCause, recommendedAction, newErrorRate, newLatency, newHealthyPods,
                replicas, newFaults, openedAt, Instant.now(), resolvedAt);
    }
}
