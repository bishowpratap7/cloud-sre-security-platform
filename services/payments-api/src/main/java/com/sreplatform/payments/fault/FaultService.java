package com.sreplatform.payments.fault;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds the set of currently injected faults and evaluates whether a given
 * request should be affected. Faults are applied probabilistically and clear
 * themselves after {@code durationSeconds} so the platform can demonstrate
 * self-healing.
 */
@Service
public class FaultService {

    private static final Logger log = LoggerFactory.getLogger(FaultService.class);

    private final ConcurrentHashMap<Fault, ActiveFault> active = new ConcurrentHashMap<>();

    private final long defaultErrorRate;
    private final long defaultLatencyMs;
    private final long defaultCpuSeconds;
    private final long defaultCertRate;
    private final TaskScheduler taskScheduler;

    public FaultService(
            @Value("${app.fault-defaults.error-rate:40}") long defaultErrorRate,
            @Value("${app.fault-defaults.latency-ms:3000}") long defaultLatencyMs,
            @Value("${app.fault-defaults.cpu-seconds:10}") long defaultCpuSeconds,
            @Value("${app.fault-defaults.cert-rate:100}") long defaultCertRate,
            TaskScheduler taskScheduler) {
        this.defaultErrorRate = defaultErrorRate;
        this.defaultLatencyMs = defaultLatencyMs;
        this.defaultCpuSeconds = defaultCpuSeconds;
        this.defaultCertRate = defaultCertRate;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    void scheduleAutoClear() {
        taskScheduler.scheduleAtFixedRate(this::clearExpired, java.time.Duration.ofSeconds(2));
    }

    public List<ActiveFault> list() {
        clearExpired();
        List<ActiveFault> sorted = new ArrayList<>(active.values());
        sorted.sort(Comparator.comparing(ActiveFault::since));
        return sorted;
    }

    public synchronized ActiveFault inject(Fault fault, double rate, long latencyMs,
                                           long cpuSeconds, long durationSeconds, String triggeredBy) {
        clearExpired();
        double effectiveRate = rate > 0 ? rate
                : fault == Fault.CERT_EXPIRED ? defaultCertRate : defaultErrorRate;
        long effectiveLatency = latencyMs > 0 ? latencyMs : defaultLatencyMs;
        long effectiveCpu = cpuSeconds > 0 ? cpuSeconds : defaultCpuSeconds;

        Instant now = Instant.now();
        ActiveFault injected = new ActiveFault(
                fault, effectiveRate, effectiveLatency, effectiveCpu,
                now, durationSeconds > 0 ? now.plusSeconds(durationSeconds) : null,
                triggeredBy);
        active.put(fault, injected);
        log.warn("FAULT INJECTED [{}] triggeredBy={} rate={}% expiresAt={}",
                fault.id(), triggeredBy, effectiveRate, injected.expiresAt());
        return injected;
    }

    public Optional<ActiveFault> clear(Fault fault, String triggeredBy) {
        ActiveFault removed = active.remove(fault);
        if (removed != null) {
            log.warn("FAULT CLEARED [{}] triggeredBy={}", fault.id(), triggeredBy);
        }
        return Optional.ofNullable(removed);
    }

    public void clearAll(String triggeredBy) {
        active.keySet().forEach(f -> clear(f, triggeredBy));
    }

    public boolean isActive(Fault fault) {
        clearExpired();
        return active.containsKey(fault);
    }

    public Optional<ActiveFault> get(Fault fault) {
        clearExpired();
        return Optional.ofNullable(active.get(fault));
    }

    /** True when a random draw within [0,100) falls under the fault rate. */
    public boolean hitsRate(Fault fault) {
        return get(fault).map(f -> ThreadLocalRandom.current().nextDouble(0, 100) < f.rate()).orElse(false);
    }

    public long currentLatencyMs() {
        return get(Fault.LATENCY).map(ActiveFault::latencyMs).orElse(0L);
    }

    private void clearExpired() {
        for (Fault f : active.keySet()) {
            ActiveFault af = active.get(f);
            if (af != null && af.isExpired()) {
                active.remove(f);
                log.warn("FAULT AUTO-CLEARED [{}] (TTL expired)", f.id());
            }
        }
    }
}
