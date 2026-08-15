package com.sreplatform.payments.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory payment store. Each payment is enriched with its upstream order so
 * the API surface exercises the resilience-protected dependency path.
 */
@Service
public class PaymentService {

    private final Map<String, Payment> payments = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(100_000);

    private final OrdersClient ordersClient;
    private final String version;

    public PaymentService(OrdersClient ordersClient, @Value("${app.version}") String version) {
        this.ordersClient = ordersClient;
        this.version = version;
    }

    @PostConstruct
    void seed() {
        for (int i = 0; i < 25; i++) {
            create((long) (1000 + Math.random() * 90000),
                    i % 2 == 0 ? "USD" : "EUR",
                    "ord_" + (1000 + i));
        }
    }

    public List<Payment> list() {
        return payments.values().stream().sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).toList();
    }

    public Payment get(String id) {
        Payment payment = payments.get(id);
        if (payment == null) {
            throw new PaymentNotFoundException(id);
        }
        return payment;
    }

    public Payment getWithOrder(String id) {
        Payment payment = get(id);
        OrderReference order = ordersClient.fetchOrder(payment.orderId());
        return new Payment(payment.id(), payment.amountCents(), payment.currency(),
                payment.orderId(), order, payment.createdAt(), payment.version());
    }

    public Payment create(long amountCents, String currency, String orderId) {
        Payment payment = new Payment(
                "pay_" + sequence.incrementAndGet(),
                amountCents,
                currency,
                orderId == null ? "ord_" + sequence.incrementAndGet() : orderId,
                OrderReference.unavailable(),
                Instant.now(),
                version);
        payments.put(payment.id(), payment);
        return payment;
    }

    public record Payment(String id, long amountCents, String currency, String orderId,
                          OrderReference order, Instant createdAt, String version) {}
}
