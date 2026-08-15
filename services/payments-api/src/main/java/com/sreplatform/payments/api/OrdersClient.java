package com.sreplatform.payments.api;

import com.sreplatform.payments.api.DependencyFailureException;
import com.sreplatform.payments.api.ExpiredCertificateException;
import com.sreplatform.payments.fault.Fault;
import com.sreplatform.payments.fault.FaultService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Downstream client to orders-api, protected by a Resilience4j circuit breaker
 * and retry. When the DEPENDENCY or CERT_EXPIRED fault is active the call is
 * deliberately failed before hitting the network — this drives the readiness
 * health indicator and the incident engine's root-cause analysis.
 */
@Component
public class OrdersClient {

    private static final Logger log = LoggerFactory.getLogger(OrdersClient.class);

    private final RestClient restClient;
    private final FaultService faultService;

    public OrdersClient(@Value("${app.orders-url}") String ordersUrl,
                        FaultService faultService) {
        this.faultService = faultService;
        this.restClient = RestClient.builder()
                .baseUrl(ordersUrl)
                .build();
    }

    @CircuitBreaker(name = "orders", fallbackMethod = "fallback")
    @Retry(name = "orders")
    public OrderReference fetchOrder(String orderId) {
        if (faultService.isActive(Fault.DEPENDENCY) && faultService.hitsRate(Fault.DEPENDENCY)) {
            log.warn("DEPENDENCY fault active — failing call to orders-api for {}", orderId);
            throw new DependencyFailureException(
                    "orders-api unavailable (simulated dependency break)");
        }
        if (faultService.isActive(Fault.CERT_EXPIRED) && faultService.hitsRate(Fault.CERT_EXPIRED)) {
            log.warn("CERT_EXPIRED fault active — simulating TLS handshake failure");
            throw new ExpiredCertificateException(
                    "Simulated expired certificate during TLS handshake with orders-api");
        }
        try {
            return restClient.get()
                    .uri("/orders/{id}", orderId)
                    .retrieve()
                    .body(OrderReference.class);
        } catch (Exception e) {
            throw new DependencyFailureException("orders-api call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback used by the circuit breaker when a call fails or the circuit is open.
     * Deliberately rethrows known failure modes so failures surface as errors for
     * the caller and are visible to monitoring; only unexpected transient blips
     * degrade gracefully.
     */
    public OrderReference fallback(String orderId, Throwable t) {
        log.warn("Circuit/fallback for order {}: {}", orderId, t.getClass().getSimpleName());
        if (t instanceof ExpiredCertificateException) {
            throw (ExpiredCertificateException) t;
        }
        if (t instanceof DependencyFailureException) {
            throw (DependencyFailureException) t;
        }
        if (t instanceof CallNotPermittedException) {
            throw new DependencyFailureException(
                    "Circuit breaker OPEN for orders-api — dependency degraded");
        }
        return OrderReference.unavailable();
    }
}
