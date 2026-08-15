package com.sreplatform.orders.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1000);
    private final long jitterMs;

    public OrderController(@Value("${app.jitter-ms:30}") long jitterMs) {
        this.jitterMs = jitterMs;
        seed();
    }

    @GetMapping
    public List<Order> list() {
        sleepJitter();
        return orders.values().stream().toList();
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable String id) {
        sleepJitter();
        Order order = orders.get(id);
        if (order == null) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }

    private void seed() {
        for (int i = 0; i < 40; i++) {
            String id = "ord_" + (1000 + i);
            orders.put(id, new Order(id, "shipped",
                    List.of("item-" + (i % 5)), 100 + i * 10L));
        }
    }

    /** Small random jitter to make latency measurements realistic. */
    private void sleepJitter() {
        if (jitterMs <= 0) {
            return;
        }
        try {
            Thread.sleep((long) (Math.random() * jitterMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record Order(String id, String status, List<String> items, long totalCents) {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class OrderNotFoundException extends RuntimeException {
        OrderNotFoundException(String id) {
            super("Order not found: " + id);
        }
    }
}
