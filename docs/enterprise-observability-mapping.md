# Enterprise observability mapping

This platform is intentionally built on open standards so every signal maps
1:1 onto commercial observability backends. Swap the OTLP exporter or the
Prometheus scrape target and the same code produces the same telemetry.

## Signal → tool mapping

| Signal | Local (this repo) | Enterprise alternative | Same semantics |
|---|---|---|---|
| Metrics | Micrometer `/actuator/prometheus` + Prometheus | Datadog (`micrometer-registry-datadog`), New Relic (`micrometer-registry-new-relic`), Grafana Cloud (Prometheus remote-write) | `http_server_requests_seconds_count{bucket,status}` — same SLO math |
| Traces | OpenTelemetry Java agent → OTLP → collector | Datadog (`DD_TRACE_*`), Splunk (`otel.*`), New Relic OTLP endpoint, Grafana Tempo | OTLP is the standard; the agent config in each `Dockerfile` is the only change |
| Logs | OTLP logs pipeline (Loki / OpenSearch compatible) | Splunk HEC, Datadog agent, New Relic logs | `OTEL_LOGS_EXPORTER=otlp` today, or switch to a vendor exporter |
| Alerting | Prometheus rules → Alertmanager → webhook | Datadog monitors / Splunk alert actions, PagerDuty on-call | Thresholds mirrored in `slo-alerts.yml` |
| Incidents | incident-engine (in-process) | Datadog incident management, PagerDuty | The `POST /incidents` endpoint is the integration seam |
| Dashboards | Grafana (provisioned, `slo-dashboard.json`) | Datadog / Grafana Cloud dashboards | Same panels: error rate, p95 latency, req/s, uptime |

## How to switch to a vendor

1. **Datadog**: add `io.micrometer:micrometer-registry-datadog`, set
   `management.datadog.metrics.export.api-key` / `uri`; replace the OTel agent
   with the Datadog trace agent, or keep OTLP and point it at Datadog's OTLP
   intake (`OTEL_EXPORTER_OTLP_ENDPOINT=https://api.datadoghq.com`, API key auth).
2. **New Relic**: `OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net` with
   `OTEL_EXPORTER_OTLP_HEADERS=api-key=<NR_LICENSE_KEY>`; no code changes.
3. **Splunk**: `OTEL_EXPORTER_OTLP_ENDPOINT=https://<realm>.signalfx.com` with
   the Splunk access token; logs via the Splunk OTel agent distro.
4. **Grafana Cloud**: keep Prometheus remote-write for metrics and use a Tempo
   backend for OTLP traces — this repo's collector config is already shaped
   that way.

## SLO definitions (shared)

| SLO | Error budget rule | Alert |
|---|---|---|
| Availability | 5xx / total requests < 20% over 5m | `HighErrorRate` (critical) |
| Latency | p95 < 1.5s over 5m | `HighLatencyP95` (warning) |
| Uptime | target up for 1m | `ServiceUnavailable` (critical) |

The incident engine consumes the same thresholds to raise incidents with root
cause and recommended action, so tooling and the on-call loop stay consistent
whether you run locally, on EKS, or with a vendor backend.
