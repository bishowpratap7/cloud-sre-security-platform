package com.sreplatform.payments.api;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String id) {
        super("Payment not found: " + id);
    }
}
