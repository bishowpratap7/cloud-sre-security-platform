package com.sreplatform.payments.fault;

import com.sreplatform.payments.observability.RequestOutcomeTracker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Applies injected latency / error / CPU faults to business traffic only
 * (health, metrics and fault endpoints stay healthy so the platform itself
 * keeps working and can observe the incident).
 */
@Component
@Order(1)
public class FaultFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FaultFilter.class);
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/actuator", "/faults", "/metrics/self", "/info");

    private final FaultService faultService;
    private final RequestOutcomeTracker outcomes;

    public FaultFilter(FaultService faultService, RequestOutcomeTracker outcomes) {
        this.faultService = faultService;
        this.outcomes = outcomes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.nanoTime();
        boolean success = true;

        try {
            // CPU exhaustion — deliberate busy loop.
            if (faultService.isActive(Fault.CPU) && faultService.hitsRate(Fault.CPU)) {
                long spins = faultService.get(Fault.CPU)
                        .map(a -> a.cpuSeconds() * 5_000_000L).orElse(50_000_000L);
                busySpin(spins);
            }

            // Latency injection.
            if (faultService.isActive(Fault.LATENCY) && faultService.hitsRate(Fault.LATENCY)) {
                long sleepMs = faultService.currentLatencyMs();
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            // HTTP 500 injection.
            if (faultService.isActive(Fault.ERROR_500) && faultService.hitsRate(Fault.ERROR_500)) {
                success = false;
                log.warn("FaultFilter: returning HTTP 500 for {} {}", request.getMethod(), request.getRequestURI());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Simulated failure (fault injection)");
                return;
            }

            filterChain.doFilter(request, response);
            success = response.getStatus() < 400;
        } catch (IOException | ServletException e) {
            success = false;
            throw e;
        } finally {
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            outcomes.record(success, latencyMs);
        }
    }

    private void busySpin(long iterations) {
        long x = 0;
        for (long i = 0; i < iterations; i++) {
            x += (i * 31L) % 97L;
        }
        if (x == Long.MIN_VALUE) {
            log.debug("unreachable"); // keep the loop side-effectful
        }
    }
}
