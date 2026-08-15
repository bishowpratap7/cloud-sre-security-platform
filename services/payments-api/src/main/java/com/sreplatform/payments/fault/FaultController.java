package com.sreplatform.payments.fault;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * "Break My Production Environment" API. The dashboard renders buttons that
 * hit these endpoints to deliberately degrade the service, then the platform
 * detects the resulting incident.
 */
@RestController
@RequestMapping("/faults")
public class FaultController {

    private final FaultService faultService;

    public FaultController(FaultService faultService) {
        this.faultService = faultService;
    }

    @GetMapping
    public List<ActiveFault> listFaults() {
        return faultService.list();
    }

    @PostMapping("/{id}/inject")
    public Map<String, Object> inject(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") double rate,
            @RequestParam(defaultValue = "0") long latencyMs,
            @RequestParam(defaultValue = "0") long cpuSeconds,
            @RequestParam(defaultValue = "0") long durationSeconds) {
        Fault fault = Fault.fromId(id);
        ActiveFault injected = faultService.inject(fault, rate, latencyMs, cpuSeconds,
                durationSeconds, "api");
        return response("injected", injected);
    }

    @PostMapping("/{id}/clear")
    public Map<String, Object> clear(@PathVariable String id) {
        Fault fault = Fault.fromId(id);
        return response("cleared", faultService.clear(fault, "api").orElse(null));
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        faultService.clearAll("api");
        return Map.of("status", "ok", "activeFaults", faultService.list().size());
    }

    private Map<String, Object> response(String status, ActiveFault fault) {
        Map<String, Object> faultJson = null;
        if (fault != null) {
            faultJson = Map.of(
                    "id", fault.fault().id(),
                    "severity", fault.fault().defaultSeverity(),
                    "rate", fault.rate(),
                    "expiresAt", String.valueOf(fault.expiresAt()));
        }
        return Map.of(
                "status", status,
                "activeFaults", faultService.list().size(),
                "fault", faultJson);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badFaultId(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }
}
