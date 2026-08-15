package com.sreplatform.incident.service;

import com.sreplatform.incident.config.MonitoredServices;
import com.sreplatform.incident.config.ServiceTarget;
import com.sreplatform.incident.model.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes incident actions. The rollback clears injected faults on the target
 * (the local equivalent of "rollback deployment") and returns the exact
 * production command that would run against EKS.
 */
@Service
public class IncidentActions {

    private static final Logger log = LoggerFactory.getLogger(IncidentActions.class);

    private final MonitoredServices monitoredServices;
    private final RestClient restClient;

    public IncidentActions(MonitoredServices monitoredServices) {
        this.monitoredServices = monitoredServices;
        this.restClient = RestClient.builder().build();
    }

    public Map<String, Object> rollback(Incident incident) {
        ServiceTarget target = monitoredServices.byName(incident.service());
        String kubectl = "kubectl rollout undo deployment/" + target.name()
                + " -n sre-platform && kubectl rollout status deployment/" + target.name() + " -n sre-platform";

        String result = "no-faults";
        try {
            restClient.post()
                    .uri(target.url() + "/faults/reset")
                    .retrieve()
                    .body(String.class);
            result = "faults-cleared";
        } catch (Exception e) {
            log.warn("Rollback: could not clear faults on {}: {}", target.name(), e.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("incidentId", incident.id());
        response.put("action", "ROLLBACK");
        response.put("status", result.equals("faults-cleared") ? "SUCCESS" : "PARTIAL");
        response.put("executed", result);
        response.put("productionCommand", kubectl);
        response.put("notes", List.of(
                "Local simulation: cleared injected faults on " + target.name(),
                "Production: run `" + kubectl + "` against EKS"));
        return response;
    }

    public Map<String, Object> playbook(Incident incident) {
        Map<String, Object> playbook = new LinkedHashMap<>();
        playbook.put("incidentId", incident.id());
        playbook.put("service", incident.service());
        playbook.put("severity", incident.severity().label());
        playbook.put("rootCause", incident.rootCause());
        playbook.put("phases", List.of(
                phase("1. Preparation",
                        List.of("Runbook owned by: payments squad",
                                "Access to EKS cluster: kubectl --context <cluster>",
                                "PagerDuty/escalation path: SEV-1 -> on-call eng + SRE lead")),
                phase("2. Detection",
                        List.of("Alert: " + incident.detectedBy() + " on " + incident.signal(),
                                "Source: Prometheus alert 'HighErrorRate' / OTel trace sampling",
                                "Confirm blast radius: affected deployments, pods, namespaces")),
                phase("3. Containment",
                        List.of("If dependency: ensure circuit breaker OPEN - traffic shed from " + incident.service(),
                                "Optionally scale down deployable from ingress: kubectl scale deployment "
                                        + incident.service() + " -n sre-platform --replicas=1",
                                "Freeze changes to the affected service")),
                phase("4. Eradication",
                        List.of("Rollback: kubectl rollout undo deployment/" + incident.service() + " -n sre-platform",
                                "Verify no faulty pods remain: kubectl get pods -n sre-platform -l app=" + incident.service(),
                                "Clear injected faults: POST /faults/reset on " + incident.service())),
                phase("5. Recovery",
                        List.of("Wait for readiness: kubectl rollout status deployment/" + incident.service() + " -n sre-platform",
                                "Watch SLO burn: error rate < 1%, p95 latency < " + incident.p95LatencyMs() + "ms",
                                "Confirm HPA returns replicas to target")),
                phase("6. Lessons Learned",
                        List.of("Post-incident review (PIR) within 72h",
                                "Add regression test / SLO alert if gap found",
                                "Update runbook: " + incident.service()))));
        playbook.put("awsReference",
                "AWS Incident Response Guide - https://aws.amazon.com/premiumsupport/technology/incident-response/");
        return playbook;
    }

    private Map<String, Object> phase(String title, List<String> steps) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", title);
        map.put("steps", steps);
        return map;
    }
}
