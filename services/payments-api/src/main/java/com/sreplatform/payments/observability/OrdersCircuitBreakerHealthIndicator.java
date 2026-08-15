package com.sreplatform.payments.observability;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health contributor tied to the readiness group. When the orders-api circuit
 * breaker opens (dependency broken), the readiness probe fails and Kubernetes
 * stops routing traffic to this pod — the platform then detects the incident
 * and can restart/rollback the deployment.
 */
@Component("ordersHealth")
public class OrdersCircuitBreakerHealthIndicator extends AbstractHealthIndicator
        implements HealthIndicator {

    private final CircuitBreakerRegistry registry;

    public OrdersCircuitBreakerHealthIndicator(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        CircuitBreaker breaker = registry.circuitBreaker("orders");
        CircuitBreaker.State state = breaker.getState();
        builder.withDetail("circuit", "orders")
                .withDetail("failureRate", String.format("%.1f%%", breaker.getMetrics().getFailureRate()));
        switch (state) {
            case OPEN -> builder.down().withDetail("state", "OPEN")
                    .withDetail("message", "orders-api dependency failing — circuit open");
            case HALF_OPEN -> builder.down().withDetail("state", "HALF_OPEN");
            default -> builder.up().withDetail("state", "CLOSED");
        }
    }
}
