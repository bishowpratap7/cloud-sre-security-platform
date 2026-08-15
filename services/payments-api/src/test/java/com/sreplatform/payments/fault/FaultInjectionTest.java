package com.sreplatform.payments.fault;

import com.sreplatform.payments.observability.RequestOutcomeTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FaultInjectionTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FaultService faultService;

    @BeforeEach
    void resetFaults() {
        faultService.clearAll("test");
    }

    @Test
    void injectErrorFaultDegradesTheService() throws Exception {
        mockMvc.perform(post("/faults/http-500/inject")
                        .param("rate", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("injected"));

        // Business traffic now returns 500s.
        mockMvc.perform(get("/payments"))
                .andExpect(status().isInternalServerError());

        // Self metrics report the degradation + active fault.
        mockMvc.perform(get("/metrics/self"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.activeFaults[0]").value("http-500"))
                .andExpect(jsonPath("$.errorRate").isNumber());

        // Clearing the fault restores service.
        mockMvc.perform(post("/faults/http-500/clear"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payments"))
                .andExpect(status().isOk());
    }

    @Test
    void injectLatencyRaisesObservedLatency() throws Exception {
        mockMvc.perform(post("/faults/inject-latency/inject")
                        .param("rate", "100")
                        .param("latencyMs", "2000"))
                .andExpect(status().isOk());

        long start = System.currentTimeMillis();
        mockMvc.perform(get("/payments"))
                .andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(1900);

        mockMvc.perform(get("/metrics/self"))
                .andExpect(jsonPath("$.p95LatencyMs").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1900.0)));

        mockMvc.perform(post("/faults/reset"));
    }

    @Test
    void dependencyFaultTripsCircuitBreakerAndReadiness() throws Exception {
        mockMvc.perform(post("/faults/break-dependency/inject")
                        .param("rate", "100"))
                .andExpect(status().isOk());

        // Drive enough failures to open the circuit (slidingWindow 20, min 5).
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(get("/payments/pay_100001/with-order"))
                    .andExpect(status().isServiceUnavailable());
        }

        // The circuit breaker health indicator must now fail the readiness group.
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.components.ordersHealth.status").value("DOWN"));

        mockMvc.perform(post("/faults/reset"));
    }

    @Test
    void expiredCertFaultReturnsUpstreamTlsError() throws Exception {
        mockMvc.perform(post("/faults/expired-certificate/inject")
                        .param("rate", "100"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/payments/pay_100001/with-order"))
                .andExpect(status().is(526));

        mockMvc.perform(post("/faults/reset"));
    }

    @Test
    void unknownFaultIsRejected() throws Exception {
        mockMvc.perform(post("/faults/nope/inject"))
                .andExpect(status().isBadRequest());
    }
}
