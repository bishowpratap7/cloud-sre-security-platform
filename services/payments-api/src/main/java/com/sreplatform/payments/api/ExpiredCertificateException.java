package com.sreplatform.payments.api;

/** Simulated upstream TLS/certificate failure. */
public class ExpiredCertificateException extends RuntimeException {

    public ExpiredCertificateException(String message) {
        super(message);
    }

    public ExpiredCertificateException(String message, Throwable cause) {
        super(message, cause);
    }
}
