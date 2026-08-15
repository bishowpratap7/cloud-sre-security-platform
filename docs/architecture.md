# Architecture

## Components

```
                           ┌───────────────────────────────────────────────┐
                           │                 Dashboard (React)             │
                           │              http://localhost:8080            │
                           └───────▲──────────────────┬───────────────────┘
                                   │  /api/*          │ /api/faults, /api/payments
                          nginx proxy │               │
                    ┌───────────────┘               └───────────────┐
                    ▼                                            ▼
        ┌───────────────────┐                        ┌───────────────────┐
        │  incident-engine  │  polls /health,/metrics│     payments-api   │
        │    (8083)         │ ──────────────────────►│     (8081)         │
        │  detection,       │                        │  fault injection,  │
        │  playbooks,       │                        │  resilience4j      │
        │  rollback         │                        └─────────┬─────────┘
        └────────▲──────────┘                                  │ /orders
                 │ webhook                            ┌────────▼─────────┐
        ┌────────┴──────────┐                         │     orders-api    │
        │  Alertmanager     │                         │      (8082)       │
        │  (9093)           │                         └───────────────────┘
        └────────▲──────────┘
                 │ alerts
        ┌────────┴──────────┐        OTLP (4317/4318)      ┌───────────────────┐
        │     Prometheus    │◄──────── otel-collector ◄─────│  all services     │
        │     (9090)        │                              │  (micrometer)     │
        └────────┬──────────┘                              └───────────────────┘
                 │ scrape
        ┌────────▼──────────┐
        │      Grafana      │
        │      (3000)       │
        └───────────────────┘
```

All paths in the diagram run identically in Docker Compose (container DNS) and
in Kubernetes (`kubernetes/`), where the same pieces are pods plus a probe/HPA/
PDB/NetworkPolicy layer.

## Reliability design

### Fault injection (`payments-api`)
`FaultController` + `FaultFilter` apply a fault probabilistically to every
incoming request based on `app.faults.*` config:

| Fault | Effect |
|---|---|
| `http-500` | return HTTP 500 |
| `inject-latency` | sleep `latencyMs` |
| `cpu-exhaustion` | busy-spin `cpuSeconds` per request |
| `break-dependency` | force 503 from the orders client (also opens the breaker) |
| `expired-certificate` | throw `ExpiredCertificateException` (simulated TLS 526) |

Faults are self-expiring (TTL) and auto-cleared on a scheduled sweep.

### Resilience (`resilience4j`)
`OrdersClient.fetchOrder` is guarded with `@CircuitBreaker(name="orders",
fallbackMethod="fallback")` + `@Retry(name="orders")` + time limiter. When the
dependency breaks:
- the breaker opens, `CallNotPermittedException` is re-thrown as 503;
- the custom `OrdersCircuitBreakerHealthIndicator` flips the readiness group,
  so the pod stops receiving traffic — Kubernetes rolls out fresh replicas;
- the incident engine sees the degradation and raises an incident.

### Incident engine (`incident-engine`)
- Polls each `MONITORED_SERVICES` entry (format `name=url,replicas,version`
  pairs separated by `;`) every 5s, sampling error rate, p95 latency, request
  count, health, replica availability.
- Detection thresholds: error rate ≥ 20% **or** p95 ≥ 1500ms.
- Debounce: 2 consecutive bad polls to open an incident, 3 consecutive good
  polls to auto-resolve.
- Severity: `SEV-1` for error-rate / availability failures, `SEV-2` otherwise.
- Root cause: derived from the signal (e.g. error-rate → "bad release" +
  rollback recommendation; latency → dependency slow / resource contention;
  dependency 5xx → downstream breakage).

### Self-healing (Kubernetes)
- `startupProbe` (readiness), `livenessProbe`, `readinessProbe` on 8080.
- `HorizontalPodAutoscaler` CPU 70% / memory 80%, 3–12 replicas.
- `PodDisruptionBudget` min 2 available.
- RollingUpdate `maxUnavailable: 0`, `terminationGracePeriodSeconds: 40`,
  graceful shutdown (Spring `server.shutdown: graceful`).

## Security design

- Namespace `sre-platform` enforces Pod Security `restricted`.
- Every workload: non-root, `readOnlyRootFilesystem`, `capabilities.drop: ALL`,
  `allowPrivilegeEscalation: false`, `seccompProfile: RuntimeDefault`.
- NetworkPolicies: `default-deny-all` plus per-service allow rules (ingress
  from dashboard/prometheus/alertmanager, egress to orders/collector/DNS).
- CI: Semgrep SAST, Trivy (vulns, secrets, licenses, IaC misconfig), image
  scans with SARIF upload; deploy only after all gates pass.

## Observability data flow

- **Metrics**: Micrometer exposes `/actuator/prometheus`; the OTel Java agent
  also exports OTLP metrics/traces to the collector which re-exports on `:9464`.
  Prometheus scrapes both. SLO alert rules fire → Alertmanager → webhook →
  incident-engine.
- **Traces**: OTLP → collector (configured for Tempo/Grafana backend in the
  enterprise mapping doc).
- **Logs**: structured logs via the agent's OTLP logs pipeline.

## Configuration

- `services/*/application.yml` — thresholds, fault defaults, resilience
  settings, observability source names.
- `docker-compose.yml` — the local simulator; image tags `sre/*:local`.
- `kubernetes/base/kustomization.yaml` — in-cluster definitions; overlays in
  `kubernetes/overlays/{kind,production}`.
