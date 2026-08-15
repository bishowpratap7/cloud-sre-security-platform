package com.sreplatform.payments.api;

import com.sreplatform.payments.api.DependencyFailureException;
import com.sreplatform.payments.api.ExpiredCertificateException;
import com.sreplatform.payments.api.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentService.Payment> list() {
        return paymentService.list();
    }

    @GetMapping("/{id}")
    public PaymentService.Payment get(@PathVariable String id) {
        return paymentService.get(id);
    }

    @GetMapping("/{id}/with-order")
    public PaymentService.Payment getWithOrder(@PathVariable String id) {
        return paymentService.getWithOrder(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentService.Payment create(@RequestBody CreatePaymentRequest request) {
        return paymentService.create(request.amountCents(), request.currency(), request.orderId());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ExpiredCertificateException.class)
    public ResponseEntity<Map<String, Object>> certError(ExpiredCertificateException e) {
        log.warn("TLS error returned to client: {}", e.getMessage());
        return ResponseEntity.status(526)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DependencyFailureException.class)
    public ResponseEntity<Map<String, Object>> dependencyError(DependencyFailureException e) {
        log.warn("Dependency error returned to client: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", e.getMessage()));
    }

    public record CreatePaymentRequest(long amountCents, String currency, String orderId) {}
}
