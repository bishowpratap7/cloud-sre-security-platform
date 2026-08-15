package com.sreplatform.payments.observability;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A tiny in-memory sliding window of request outcomes used to derive the
 * demo's error rate and latency. In production these numbers come from
 * Prometheus/OpenTelemetry — see observability/prometheus/alerts/slo-alerts.yml.
 */
@Component
public class RequestOutcomeTracker {

    private static final long WINDOW_MS = 60_000L;

    private record Sample(long timestampMs, boolean success, long latencyMs) {}

    private final Deque<Sample> samples = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void record(boolean success, long latencyMs) {
        lock.lock();
        try {
            samples.addLast(new Sample(Instant.now().toEpochMilli(), success, latencyMs));
            evict();
        } finally {
            lock.unlock();
        }
    }

    public double errorRate() {
        lock.lock();
        try {
            evict();
            if (samples.isEmpty()) {
                return 0.0;
            }
            long errors = samples.stream().filter(s -> !s.success).count();
            return 100.0 * errors / samples.size();
        } finally {
            lock.unlock();
        }
    }

    public long p95LatencyMs() {
        lock.lock();
        try {
            evict();
            if (samples.isEmpty()) {
                return 0L;
            }
            return samples.stream()
                    .mapToLong(Sample::latencyMs)
                    .sorted()
                    .skip((long) (samples.size() * 0.95))
                    .findFirst()
                    .orElse(0L);
        } finally {
            lock.unlock();
        }
    }

    public long requestCount() {
        lock.lock();
        try {
            evict();
            return samples.size();
        } finally {
            lock.unlock();
        }
    }

    private void evict() {
        long cutoff = Instant.now().toEpochMilli() - WINDOW_MS;
        while (!samples.isEmpty() && samples.peekFirst().timestampMs < cutoff) {
            samples.removeFirst();
        }
    }
}
