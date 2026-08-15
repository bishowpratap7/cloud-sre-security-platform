package com.sreplatform.payments.api;

public record OrderReference(String orderId, String status) {

    public static OrderReference unavailable() {
        return new OrderReference("unknown", "unavailable");
    }
}
