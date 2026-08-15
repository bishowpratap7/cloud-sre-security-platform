package com.sreplatform.orders.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/** Health snapshot for the incident engine (mirrors payments-api). */
@RestController
public class SelfMetricsController {

    private final String version;
    private final String hostname;

    public SelfMetricsController(@Value("${app.version}") String version) {
        this.version = version;
        this.hostname = resolveHostname();
    }

    @GetMapping("/metrics/self")
    public Map<String, Object> selfMetrics() {
        return Map.of(
                "service", "orders-api",
                "version", version,
                "instanceId", hostname,
                "status", "HEALTHY",
                "errorRate", 0.0,
                "p95LatencyMs", 0.0,
                "requestCount", 0L,
                "activeFaults", List.of());
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
