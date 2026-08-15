package com.sreplatform.payments.api;

/** Simulated failure of a downstream dependency (orders-api). */
public class DependencyFailureException extends RuntimeException {

    public DependencyFailureException(String message) {
        super(message);
    }

    public DependencyFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
