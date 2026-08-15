package com.sreplatform.payments.fault;

/**
 * Faults that can be injected into a service to simulate production incidents.
 * These power the "Break My Production Environment" workflow.
 */
public enum Fault {
    LATENCY("inject-latency",
            "Adds artificial latency to a percentage of requests",
            "SEV-2"),
    ERROR_500("http-500",
            "Returns HTTP 500 to a percentage of requests",
            "SEV-1"),
    CPU("cpu-exhaustion",
            "Busy-spins the CPU on a percentage of requests",
            "SEV-2"),
    DEPENDENCY("break-dependency",
            "Breaks the downstream orders-api dependency (503 + circuit breaker)",
            "SEV-1"),
    CERT_EXPIRED("expired-certificate",
            "Simulates an upstream TLS/certificate failure",
            "SEV-1");

    private final String id;
    private final String description;
    private final String defaultSeverity;

    Fault(String id, String description, String defaultSeverity) {
        this.id = id;
        this.description = description;
        this.defaultSeverity = defaultSeverity;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public String defaultSeverity() {
        return defaultSeverity;
    }

    public static Fault fromId(String id) {
        for (Fault f : values()) {
            if (f.id.equalsIgnoreCase(id)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Unknown fault: " + id);
    }
}
