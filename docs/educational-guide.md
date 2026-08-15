# Learn SRE & Cloud Security by Breaking Local "Production" On Your Laptop (On Purpose)

> **Author: Bishow Pandey** — built as an educational platform so anyone can
> learn Site Reliability Engineering, cloud security, and modern DevOps by
> breaking and fixing a real, production-shaped system — safely.

This is a **teaching project**: a miniature, production-shaped AWS + Kubernetes
platform that you can run on your laptop and deliberately break — so you can
watch an entire SRE incident lifecycle happen in real time.

It is designed for *everyone*, regardless of background:

- **Students** — see real Java, Kubernetes, and monitoring working together.
- **Junior / mid-level engineers** — practice ops skills safely, on your own
  machine.
- **SREs and platform engineers** — a compact, readable reference for how
  detection, playbooks, rollback, and SLO alerting fit together.
- **Security-minded folks** — a concrete example of "secure by default"
  manifests, network policies, and a CI pipeline with security gates.
- **Managers / non-engineers** — an interactive story of what happens when
  software breaks in production and how a platform automates the response.

---

## 1. What this project is (in one paragraph)

Three small Java services run like they would in a real cloud. One of them
(`payments-api`) can be **broken on demand** — return HTTP 500s, add latency,
burn CPU, break its dependency, or simulate an expired TLS certificate. A
fourth service, the **incident engine**, watches the services every 5 seconds,
and when something degrades past a threshold it raises a **SEV-1/SEV-2
incident**, tells you the *root cause* and the *recommended action*, shows you
a 6-phase incident-response playbook, and can **roll back** the bad release.
Everything is observable (Prometheus, Grafana, OpenTelemetry), everything is
repeatable (Docker Compose and Kind), and everything is secure-by-default.

The most important thing it teaches is this:

> **In production, things will break. The goal is not to prevent all breakage —
> it is to detect it fast, understand it quickly, and recover safely.**

## 2. What you'll learn

| Area | What you'll actually see |
|---|---|
| **Software reliability (SRE)** | SLOs, error budgets, incident severity, debounce, root-cause analysis, playbooks, rollback |
| **Resilience patterns** | Circuit breaker, retry, time limiter, graceful shutdown, fallbacks |
| **Kubernetes** | Deployments, probes (liveness/readiness/startup), HPA, PodDisruptionBudget, rolling updates, self-healing |
| **Security** | Pod Security "restricted", non-root + read-only containers, NetworkPolicies, default-deny |
| **Observability** | Metrics, traces, logs — and why you need all three |
| **Alerting** | Prometheus rules → Alertmanager → a "who to page" sink |
| **CI/CD & DevSecOps** | SAST, dependency scanning, container scanning, IaC scanning, deploy pipeline |
| **IaC** | Terraform provisioning an EKS cluster from scratch |
| **Distributed systems** | Service discovery, dependency failure propagation, graceful degradation |

## 3. How the pieces fit together

```
        you break it here                      it gets detected here
   ┌────────────────────────┐        ┌──────────────────────────────┐
   │ payments-api (8081)    │        │ incident-engine (8083)       │
   │  - HTTP 500s           │◄──────►│  - polls every 5s            │
   │  - latency             │  talks  │  - debounce + severity       │
   │  - CPU exhaust         │  to     │  - root cause + action       │
   │  - broken dependency   │  orders │  - playbook + rollback       │
   │  - expired cert        │  -api   └──────────────┬───────────────┘
   └───────────┬────────────┘ (8082)                 │
               │ metrics                             │ incidents
        ┌──────▼──────────────┐                ┌─────▼──────────────┐
        │ OpenTelemetry +     │                │  Dashboard (8080)  │
        │ Prometheus + Grafana│◄──────────────►│  Incident Command  │
        └─────────────────────┘  SLO alerts    └────────────────────┘
```

Think of it as a loop:

1. **Traffic** flows into `payments-api`.
2. You **inject a fault** ("Break My Production Environment").
3. Metrics get **worse** (error rate up, latency up, or dependency down).
4. The **incident engine detects** the degradation and opens an incident.
5. The **playbook** tells a human what to do; **rollback** does the safe fix.
6. Health recovers → the incident **auto-resolves**.

## 4. Before you start (prerequisites)

| Tool | Why | Install |
|---|---|---|
| **Git** | get the code | https://git-scm.com |
| **Docker + Docker Compose** | run the whole stack locally | https://docs.docker.com/get-docker |
| **Java 21** | build/run the Spring Boot services | https://adoptium.net |
| **Maven 3.8+** | build the Java code | https://maven.apache.org |
| **Node 20+ & npm** | build the dashboard | https://nodejs.org |
| *Optional:* **Kind + kubectl** | run a real Kubernetes cluster locally | https://kind.sigs.k8s.io |

> On Windows, run the commands below in **PowerShell**; on macOS/Linux use a
> terminal. Docker Desktop must be running.
>
> **Windows shortcut:** double-click **`run.bat`** instead — a menu-driven
> launcher with options to start the Docker stack, run everything locally
> without Docker (Java jars + Vite dev on `:5173`), stop, or check status.
> If one service fails to start, it warns you and continues with the rest.

## 5. Run it (5-minute quick start)

```bash
# 1. Get the code
git clone <your-repo-url>
cd cloud-sre-security-platform

# 2. Build the Java services and the dashboard
mvn -f services/pom.xml clean package -DskipTests
npm --prefix dashboard install
npm --prefix dashboard run build

# 3. Start the full stack (services + observability + dashboard)
docker compose up --build -d
```

When it's ready you'll have:

| URL | What | Credentials |
|---|---|---|
| http://localhost:8080 | **Dashboard** — Incident Command | none |
| http://localhost:8081 | payments-api (health + faults) | none |
| http://localhost:8082 | orders-api | none |
| http://localhost:8083 | incident-engine (incidents + services) | none |
| http://localhost:9090 | Prometheus metrics | none |
| http://localhost:9093 | Alertmanager | none |
| http://localhost:3000 | Grafana dashboards | `admin` / `admin` |

Check everything is healthy:

```bash
docker compose ps          # all containers should be running/healthy
curl http://localhost:8083/incidents/active   # should print []
```

## 6. The demo — break production, watch it get fixed (10 minutes)

This is the heart of the project. Open the dashboard at
**http://localhost:8080**, then:

**Step 1 — Generate traffic** (so the services have real metrics to trip on).
Click the **traffic on** toggle, or in a terminal run:

```bash
for i in $(seq 1 30); do curl -s -o /dev/null http://localhost:8081/payments; done
```

**Step 2 — Break it.** On the dashboard click **HTTP 500s** (or run):

```bash
curl -X POST "http://localhost:8081/faults/http-500/inject?rate=100&durationSeconds=300"
```

**Step 3 — Watch detection.** Within ~15 seconds an incident appears:

```bash
curl http://localhost:8083/incidents/active
```

You'll see something like:

```
INC-1  payments-api  SEV-1  status=OPEN
  signal:  http_server_requests_total 5xx
  root cause:  Service returning HTTP 500s — bad release 1.8.3
  recommended action:  Rollback deployment to last known good
  detected by:  Prometheus (http_server_requests_total) + OpenTelemetry (OTLP)
```

**Step 4 — Follow the playbook.**

```bash
curl -X POST http://localhost:8083/incidents/INC-1/playbook
```

**Step 5 — Fix it (rollback).**

```bash
curl -X POST http://localhost:8083/incidents/INC-1/rollback
```

Faults are cleared, the command to actually roll back a deployment is printed,
and the incident **auto-resolves** once health is back.

Try the other faults and watch how the diagnosis changes:

```bash
curl -X POST "http://localhost:8081/faults/inject-latency/inject?rate=100&latencyMs=3000"
curl -X POST "http://localhost:8081/faults/cpu-exhaustion/inject?rate=100&cpuSeconds=10"
curl -X POST "http://localhost:8081/faults/break-dependency/inject?rate=100"
curl -X POST "http://localhost:8081/faults/expired-certificate/inject?rate=100"
curl -X POST "http://localhost:8081/faults/reset"      # clear everything
```

> Prefer a GUI? The dashboard has buttons for all of this, plus **View Logs**,
> **View Trace**, and the full **Incident Playbook**.

## 7. Run a real Kubernetes cluster (Kind, ~10 minutes)

Compose simulates production; Kind *is* Kubernetes.

```bash
# 1. Create a 1 control-plane + 2 workers cluster
kind create cluster --config scripts/kind/kind-config.yaml

# 2. Build and load the images into the cluster
docker build -t sre/payments-api:local -t sre/orders-api:local \
             -t sre/incident-engine:local -t sre/dashboard:local .
kind load docker-image sre/payments-api:local sre/orders-api:local \
     sre/incident-engine:local sre/dashboard:local

# 3. Deploy everything (namespace, apps, HPA, PDB, NetworkPolicies, observability)
kubectl apply -k kubernetes/overlays/kind

# 4. See it all come up
kubectl -n sre-platform get pods,svc,hpa,pdb,networkpolicy
```

Now you can learn Kubernetes by doing:

```bash
# Watch the deployment roll out safely (maxUnavailable=0)
kubectl -n sre-platform rollout status deployment/payments-api

# Force self-healing: delete a pod, watch the ReplicaSet recreate it
kubectl -n sre-platform delete pod -l app=payments-api

# Scale with HPA
kubectl -n sre-platform get hpa -w

# Simulate the production rollback the incident engine recommends
kubectl -n sre-platform rollout undo deployment/payments-api
```

Port-forward to reach the UIs: `kubectl -n sre-platform port-forward svc/dashboard 8080:80`.

## 8. What the "educational magic" is

**Every concept is visible.** Because everything runs locally, you can open the
Prometheus query UI and *watch* the error rate climb the moment you inject a
fault, then fall when you roll back:

```
sum(rate(http_server_requests_seconds_count{status="500"}[5m]))
```

You can **read every line of the incident logic** in
`services/incident-engine/src/main/java/.../IncidentEngineService.java` — the
whole detection algorithm is ~300 lines of readable Java, not a black box.

You can **read every manifest** in `kubernetes/` and see exactly why a pod is
safe: non-root user, read-only filesystem, dropped capabilities, a network
policy that only allows the traffic it needs.

You can **inspect the CI pipeline** in `.github/workflows/ci-cd.yml` and see
each security gate in sequence — tests → SAST → dependency scan → IaC scan →
build → image scan → deploy.

And because it's all **open standards** (OpenTelemetry, Prometheus, Kubernetes,
Terraform), the exact same telemetry and manifests map onto commercial
platforms (Datadog, New Relic, Grafana Cloud, Splunk) — see
`docs/enterprise-observability-mapping.md`.

## 9. Guided learning paths

**Path A — "I've never seen a cloud service" (30–60 min)**
1. Run the quick start (§5) and the demo (§6).
2. Change a threshold in `services/incident-engine/src/main/resources/application.yml`
   (`error-rate-threshold: 20.0` → `10.0`) and rebuild; see detection get
   *more* sensitive.
3. Read `docs/runbook.md` — it's the on-call cheat sheet.

**Path B — "I write code but ops scares me"**
1. Path A.
2. Read `services/payments-api`'s `OrdersClient` + `application.yml` — see the
   circuit breaker, retry, and fallback.
3. Run Path A's demo with `break-dependency` and watch the circuit open (check
   `curl localhost:8081/actuator/health/readiness`).
4. Open Grafana and build a panel from raw PromQL.

**Path C — "I'm studying for a certification / interview"**
1. Paths A + B.
2. Add a new fault type in `FaultService`/`FaultFilter` and a matching diagnosis
   branch in the incident engine — then prove it works with a test.
3. Explain, out loud, the detection loop (poll → debounce → severity → action)
   and the self-healing loop (probe → readiness → rollout).
4. Use `docs/architecture.md` to quiz yourself on each box in the diagram.

**Path D — "I'm security-focused"**
1. Path A.
2. Read `kubernetes/base/networkpolicy-default.yaml` and each app's policy.
   Try to find a rule that is too permissive — then tighten it.
3. Run `scripts/ci-local.sh` and read each scan's output.
4. `kubectl get networkpolicy -n sre-platform` and explain what each one allows.

## 10. Glossary — SRE words you'll now know

| Term | Meaning (plain English) |
|---|---|
| **SLO** | Service Level Objective — the promise (e.g. "error rate under 20%"). |
| **Error budget** | How much of the promise you can afford to break before users notice. |
| **SEV-1 / SEV-2** | Severity: SEV-1 = customer-impacting, fix immediately; SEV-2 = degraded, fix soon. |
| **Debounce** | Requiring N consecutive bad readings before declaring an incident (avoids flapping). |
| **Root cause** | The underlying reason — what the incident engine tries to infer for you. |
| **Playbook** | The checklist humans follow during an incident. |
| **Rollback** | Undoing a bad release to the last known-good version. |
| **Circuit breaker** | Stops calling a failing dependency so it can recover and we fail fast. |
| **Readiness probe** | "Am I ready for traffic?" If no, Kubernetes stops sending traffic. |
| **Liveness probe** | "Am I alive?" If no, Kubernetes restarts the pod. |
| **HPA** | Horizontal Pod Autoscaler — adds/removes pods based on load. |
| **PDB** | PodDisruptionBudget — guarantees a minimum number of pods during maintenance. |
| **SAST** | Static application security testing — scan source code for vulnerabilities. |
| **IaC** | Infrastructure as Code — define cloud resources in versioned files. |

## 11. Troubleshooting (the first time, things that bite)

| Symptom | Fix |
|---|---|
| No incident appears after injecting a fault | The service needs *traffic*; enable the dashboard toggle or hit `/payments` a few times. |
| `docker compose` fails on port conflicts | Change the host port in `docker-compose.yml` (e.g. `8081:8080` → `18081:8080`). |
| Dashboard says "offline" | Backend containers not healthy yet — `docker compose ps` and wait for healthchecks. |
| Grafana has no data | Datasource URL `http://prometheus:9090` only resolves inside the compose network. |
| On Windows, curl errors with `&` | Quote the whole URL: `".../inject?rate=100&durationSeconds=300"`. |
| Java build memory errors | Increase Maven memory: `MAVEN_OPTS=-Xmx1g`. |

## 12. Where to go from here

- **Documentation**: `docs/architecture.md`, `docs/runbook.md`,
  `docs/enterprise-observability-mapping.md`
- **Deploy for real**: `terraform/` provisions an AWS EKS cluster; the GitHub
  Actions workflow in `.github/workflows/ci-cd.yml` ships it with security
  gates.
- **The big idea to take away**: reliability is a *practice*, not a tool. This
  project just makes the practice visible, repeatable, and safe to learn —
  which is exactly what real SRE teams do, minus the pager.

---

**Author: Bishow Pandey** · Built to teach SRE, cloud security, and modern
DevOps through hands-on experimentation.
