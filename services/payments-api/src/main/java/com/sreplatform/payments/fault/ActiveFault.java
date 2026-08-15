package com.sreplatform.payments.fault;

import java.time.Instant;

/**
 * An active fault injection. Carries the parameters used to make the fault
 * observable and a TTL after which it is cleared automatically (self-healing).
 */
public record ActiveFault(
        Fault fault,
        double rate,          // percentage of traffic affected
        long latencyMs,
        long cpuSeconds,
        Instant since,
        Instant expiresAt,
        String triggeredBy) {

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
