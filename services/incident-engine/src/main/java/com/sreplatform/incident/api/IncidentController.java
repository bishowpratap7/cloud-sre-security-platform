package com.sreplatform.incident.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sreplatform.incident.model.Incident;
import com.sreplatform.incident.model.ServiceSnapshot;
import com.sreplatform.incident.service.IncidentActions;
import com.sreplatform.incident.service.IncidentEngineService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class IncidentController {

    private final IncidentEngineService engine;
    private final IncidentActions actions;

    public IncidentController(IncidentEngineService engine, IncidentActions actions) {
        this.engine = engine;
        this.actions = actions;
    }

    @GetMapping("/incidents")
    public List<Incident> incidents() {
        return engine.allIncidents();
    }

    @GetMapping("/incidents/active")
    public List<Incident> active() {
        return engine.activeIncidents();
    }

    /** Prometheus/Alertmanager webhook → open an incident. */
    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void alertWebhook(@RequestBody JsonNode body) {
        if (engine.ingestAlert(body).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty alert webhook payload");
        }
    }

    @GetMapping("/incidents/{id}")
    public Incident incident(@PathVariable String id) {
        return engine.incident(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    @GetMapping("/incidents/{id}/playbook")
    public Map<String, Object> playbook(@PathVariable String id) {
        return actions.playbook(require(id));
    }

    @PostMapping("/incidents/{id}/ack")
    public Incident ack(@PathVariable String id) {
        return engine.incident(id)
                .map(i -> engine.updateStatus(i, Incident.Status.ACK))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    @PostMapping("/incidents/{id}/resolve")
    public Incident resolve(@PathVariable String id) {
        return engine.incident(id)
                .map(i -> engine.updateStatus(i, Incident.Status.RESOLVED))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    @PostMapping("/incidents/{id}/rollback")
    public Map<String, Object> rollback(@PathVariable String id) {
        return actions.rollback(require(id));
    }

    @GetMapping("/services")
    public List<ServiceSnapshot> services() {
        return engine.serviceStatus();
    }

    @GetMapping("/services/{name}")
    public ServiceSnapshot service(@PathVariable String name) {
        return engine.serviceStatus().stream()
                .filter(s -> s.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    private Incident require(String id) {
        return engine.incident(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }
}
