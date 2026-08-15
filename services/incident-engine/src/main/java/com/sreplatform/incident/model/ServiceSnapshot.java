package com.sreplatform.incident.model;

import java.time.Instant;
import java.util.List;

/** Snapshot of one monitored service taken during a poll cycle. */
public record ServiceSnapshot(
        String name,
        String version,
        String health,          // UP | DOWN
        String status,          // HEALTHY | DEGRADED | UNREACHABLE
        double errorRate,
        long p95LatencyMs,
        long requestCount,
        List<String> activeFaults,
        Instant observedAt) {

    public boolean healthy() {
        return "HEALTHY".equals(status);
    }

    public boolean degraded() {
        return "DEGRADED".equals(status);
    }
}
