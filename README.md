# Cloud SRE & Security Platform

A production-style **AWS + Kubernetes reliability & security platform** with a
"Break My Production Environment" fault-injection demo.

Java 21 · Spring Boot 3.3 · React 18 + Vite · EKS / Kind · Prometheus · Grafana ·
OpenTelemetry · GitHub Actions · Terraform.

**Author: Bishow Pandey**

> Learn **Site Reliability Engineering (SRE)**, **cloud security**, **Kubernetes**,
> **incident management**, and **DevOps** by breaking production on purpose —
> then watching incidents get detected, diagnosed, and rolled back automatically.
> Built by **Bishow Pandey** as an open, hands-on learning platform:
> run it with Docker Compose or Kind, inject real faults (HTTP 500s, latency,
> CPU exhaustion, broken dependencies, expired certificates), and follow the full
> incident lifecycle through observability (Prometheus, Grafana, OpenTelemetry)
> and CI/CD with security scanning (Semgrep, Trivy).

## What it does

| Capability | How |
|---|---|
| **Fault injection / chaos engineering** | "Break my production" — inject HTTP 500s, latency, CPU exhaustion, broken dependencies, expired certs into `payments-api` (`/faults/*`). |
| **Incident detection** | `incident-engine` polls every 5s, correlates health + metrics, debounces, and raises `SEV-1`/`SEV-2` incidents with auto root-cause + recommended action. |
| **Incident playbook** | 6-phase AWS IR playbook per incident (`/incidents/{id}/playbook`). |
| **Automated rollback** | One-click rollback that clears the fault and issues the production `kubectl rollout undo` command. |
| **Resilience patterns** | resilience4j circuit breaker + retry + time limiter on the orders dependency; degraded service fails readiness → pods are rolled by k8s. |
| **Observability** | Micrometer + OpenTelemetry (OTLP), Prometheus SLO alerts, Alertmanager, Grafana dashboards (all in compose and in-cluster). |
| **Security** | NetworkPolicies (default-deny), Pod Security restricted, read-only rootfs, non-root, dropped capabilities, SAST/dependency/container/IaC scans in CI. |
| **Self-healing** | Liveness/readiness probes, HPA (CPU+memory), PDB, RollingUpdate `maxUnavailable=0`, graceful shutdown. |
| **Infrastructure as Code** | Terraform provisions the AWS EKS cluster (VPC, node groups, IAM); `kubernetes/` holds the full manifest set. |

## Quick start (Docker Compose)

Requires: Docker, Java 21, Maven 3.8+, Node 20+.

```bash
make build          # mvn clean package + dashboard npm build
make up             # docker compose up --build -d
make demo-500       # inject 100% HTTP 500s -> watch an incident get created
```

| URL | What |
|---|---|
| http://localhost:8080 | Dashboard (Incident Command) |
| http://localhost:8081 | payments-api (`/actuator/health`, `/faults`) |
| http://localhost:8082 | orders-api |
| http://localhost:8083 | incident-engine (`/incidents`, `/services`) |
| http://localhost:9090 | Prometheus |
| http://localhost:9093 | Alertmanager |
| http://localhost:3000 | Grafana (admin/admin) |

## Demo: Break My Production Environment

```bash
make up

# Terminal 1 — watch the incident engine
curl -s http://localhost:8083/incidents/active | jq .      # poll this

# Terminal 2 — break it
make demo-500            # or: demo-latency | demo-cpu | demo-break-dep | demo-cert

# ~15s later: INC-1 / SEV-1 with root cause + recommended action
curl -s -X POST http://localhost:8083/incidents/INC-1/ack
curl -s -X POST http://localhost:8083/incidents/INC-1/playbook   # IR playbook
curl -s -X POST http://localhost:8083/incidents/INC-1/rollback   # fixes it

make demo-reset         # clear any remaining faults
```

Run the whole thing from the dashboard at http://localhost:8080.

## Local Kubernetes (Kind)

```bash
make kind-up            # kind create cluster --config scripts/kind/kind-config.yaml
docker build -t sre/payments-api:local -t sre/orders-api:local \
             -t sre/incident-engine:local -t sre/dashboard:local .   # or via make
kind load docker-image sre/payments-api:local sre/orders-api:local \
     sre/incident-engine:local sre/dashboard:local
make kind-apply         # kubectl apply -k kubernetes/overlays/kind
make kind-portforward   # grafana:3000, payments-api:8081, incident-engine:8083
```

In-cluster observability, probes, HPA, PDB and NetworkPolicies are all applied —
see `kubernetes/`.

## Production (EKS)

1. `terraform init && terraform apply` in `terraform/` (VPC, EKS, managed nodes).
2. Configure CI secrets (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`) —
   `.github/workflows/ci-cd.yml` runs tests → Semgrep SAST → Trivy
   (deps + IaC + images) → pushes `ghcr.io` images → deploys via kustomize.
3. `kubectl apply -k kubernetes/overlays/production` for the static bits.

## Repository layout

```
services/
  payments-api/        # main service: fault injection, resilience4j, metrics
  orders-api/          # downstream dependency
  incident-engine/     # detection + playbooks + rollback
dashboard/             # React/Vite incident-command UI (nginx)
kubernetes/            # base + kind/production overlays (probes, HPA, PDB,
                       # NetworkPolicy, prometheus rules, grafana, collector)
observability/         # compose-mounted configs (otel, prometheus, alertmanager, grafana)
terraform/             # EKS IaC (VPC, cluster, node groups, IAM)
scripts/               # fault-inject, kill-pod, ci-local, kind config
docs/                  # architecture, runbook, observability mapping
.github/workflows/     # CI/CD with security gates
```

## Documentation

- [Educational guide](docs/educational-guide.md) — what it does, how to run it,
  the guided demo, and what every piece teaches you (great starting point)
- [Architecture](docs/architecture.md) — components, data flow, reliability design
- [Runbook](docs/runbook.md) — operational procedures and the incident lifecycle
- [Enterprise observability mapping](docs/enterprise-observability-mapping.md) —
  how this maps to Datadog/Splunk/New Relic/Grafana Cloud
- [SEO / discoverability setup](docs/seo-setup.md) — description, topics, and
  metadata to maximize how this repo ranks in search
- [Terraform](terraform/README.md) — bootstrap + state locking

---

Built and maintained by **Bishow Pandey**.
