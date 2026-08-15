# Runbook

Operational procedures for the SRE platform. Assumes the compose stack
(`make up`) or the Kind cluster (`make kind-up && make kind-apply`).

## Incident lifecycle

```
DETECT ──► OPEN ──► ACK ──► (playbook / rollback / fix) ──► RESOLVED
```

1. **Detect** — Prometheus SLO alert or the incident engine's poller raises an
   incident (`SEV-1`/`SEV-2`) with root cause + recommended action.
2. **Acknowledge** — `POST /incidents/{id}/ack` (owner takes ownership).
3. **Mitigate** — `POST /incidents/{id}/rollback` clears injected faults and
   prints the production command; or follow the 6-phase playbook from
   `POST /incidents/{id}/playbook`.
4. **Resolve** — `POST /incidents/{id}/resolve` (or auto-resolve after 3 good
   polls). `GET /incidents` keeps the full history.

## API reference

| Method | Path | Purpose |
|---|---|---|
| GET | `/incidents` | all incidents (history) |
| GET | `/incidents/active` | open incidents |
| GET | `/incidents/{id}` | incident detail |
| POST | `/incidents` | Prometheus/Alertmanager webhook → open an incident |
| GET | `/incidents/{id}/playbook` | 6-phase IR playbook |
| POST | `/incidents/{id}/ack` | acknowledge |
| POST | `/incidents/{id}/resolve` | resolve |
| POST | `/incidents/{id}/rollback` | rollback + clear faults |
| GET | `/services` | monitored service snapshots |
| GET | `/services/{name}` | single service snapshot |
| GET | `/faults` | active injected faults |
| POST | `/faults/{id}/inject?rate=..` | inject fault (id: http-500, inject-latency, cpu-exhaustion, break-dependency, expired-certificate) |
| POST | `/faults/{id}/clear` | clear a fault |
| POST | `/faults/reset` | clear all faults |
| GET | `/payments` | payment endpoint (traffic target) |
| GET | `/actuator/health` | health endpoints |

## Break-my-production cheat sheet

```bash
# Watch incidents
curl -s http://localhost:8083/incidents/active | jq .

# Break things (each is a Makefile target: make demo-500, demo-latency, ...)
curl -s -X POST "http://localhost:8081/faults/http-500/inject?rate=100&durationSeconds=300" | jq .
curl -s -X POST "http://localhost:8081/faults/inject-latency/inject?rate=100&latencyMs=3000" | jq .
curl -s -X POST "http://localhost:8081/faults/cpu-exhaustion/inject?rate=100&cpuSeconds=10" | jq .
curl -s -X POST "http://localhost:8081/faults/break-dependency/inject?rate=100" | jq .
curl -s -X POST "http://localhost:8081/faults/expired-certificate/inject?rate=100" | jq .

# Fix things
curl -s -X POST http://localhost:8081/faults/reset | jq .
curl -s -X POST "http://localhost:8083/incidents/$(curl -s http://localhost:8083/incidents/active | jq -r '.[0].id')/rollback" | jq .
```

## Kubernetes operations

```bash
kubectl -n sre-platform get all
kubectl -n sre-platform get hpa,pdb,networkpolicy

# Force a redeploy / self-heal demonstration
kubectl -n sre-platform delete pod -l app=payments-api
kubectl -n sre-platform rollout restart deployment/payments-api

# Production rollback (what the engine recommends on SEV-1)
kubectl -n sre-platform rollout undo deployment/payments-api
kubectl -n sre-platform rollout status deployment/payments-api
```

## Tuning detection

Thresholds live in `services/incident-engine/src/main/resources/application.yml`:

```yaml
app:
  detection:
    poll-interval-ms: 5000
    latency-threshold-ms: 1500
    error-rate-threshold: 20.0
    detect-after-polls: 2
    clear-after-polls: 3
```

Prometheus SLO rules in `observability/prometheus/alerts/slo-alerts.yml` and
the in-cluster copy in `kubernetes/base/observability/prometheus/configmap.yaml`.

## Troubleshooting

| Symptom | Check |
|---|---|
| No incidents when fault injected | traffic? run `curl http://localhost:8081/payments` a few times, or enable traffic in the dashboard |
| Circuit never opens on dependency break | readiness group `ordersHealth`; confirm `DESTINATION_URL` points at `orders-api:8080` |
| Prometheus shows no targets | `docker compose logs prometheus`; collector on `:9464`; `http_server_requests_*` under `/actuator/prometheus` |
| Grafana no data | datasource URL `http://prometheus:9090` reachable only inside the compose network |
| Probes failing in k8s | JVM needs writable `/tmp` — the deployment mounts an `emptyDir`; verify `readOnlyRootFilesystem` |
