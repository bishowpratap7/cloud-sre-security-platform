package com.sreplatform.payments.observability;

import com.sreplatform.payments.fault.ActiveFault;
import com.sreplatform.payments.fault.FaultService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * Self-reported health snapshot used by the incident engine and dashboard.
 * Mirrors what Prometheus would compute from micrometer metrics so the demo
 * works with zero external dependencies. Metrics are also exported via
 * /actuator/prometheus and scraped by Prometheus in the compose stack.
 */
@RestController
public class SelfMetricsController {

    private final RequestOutcomeTracker outcomes;
    private final FaultService faultService;
    private final String version;
    private final String hostname;

    public SelfMetricsController(RequestOutcomeTracker outcomes,
                                 FaultService faultService,
                                 @Value("${app.version}") String version) {
        this.outcomes = outcomes;
        this.faultService = faultService;
        this.version = version;
        this.hostname = resolveHostname();
    }

    @GetMapping("/metrics/self")
    public Map<String, Object> selfMetrics() {
        List<ActiveFault> faults = faultService.list();
        return Map.of(
                "service", "payments-api",
                "version", version,
                "instanceId", hostname,
                "status", faults.isEmpty() ? "HEALTHY" : "DEGRADED",
                "errorRate", outcomes.errorRate(),
                "p95LatencyMs", (double) outcomes.p95LatencyMs(),
                "requestCount", outcomes.requestCount(),
                "activeFaults", faults.stream().map(f -> f.fault().id()).toList());
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
