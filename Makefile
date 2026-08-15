# =====================================================================
# cloud-sre-security-platform — task runner
# Local-first: Docker Compose for the stack, Kind for real K8s.
# =====================================================================
SHELL := /bin/bash
COMPOSE := docker compose

.PHONY: help
help: ## Show available tasks
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-28s\033[0m %s\n", $$1, $$2}'

# ---------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------
.PHONY: build
build: ## Build all Java services and the dashboard
	mvn -f services/pom.xml clean package -DskipTests
	npm --prefix dashboard ci
	npm --prefix dashboard run build

.PHONY: test
test: ## Run all tests (unit + integration)
	mvn -f services/pom.xml clean verify

.PHONY: java-test
java-test: ## Run Java unit/integration tests only
	mvn -f services/pom.xml clean verify

.PHONY: dashboard-build
dashboard-build: ## Build the dashboard only
	npm --prefix dashboard ci
	npm --prefix dashboard run build

# ---------------------------------------------------------------------
# Local stack (Docker Compose)
# ---------------------------------------------------------------------
.PHONY: up
up: ## Start the full local stack (services + observability + dashboard)
	$(COMPOSE) up --build -d
	@echo "Dashboard:      http://localhost:8080"
	@echo "Payments API:   http://localhost:8081/actuator/health"
	@echo "Orders API:     http://localhost:8082/actuator/health"
	@echo "Incident engine:http://localhost:8083/incidents"
	@echo "Prometheus:     http://localhost:9090"
	@echo "Grafana:        http://localhost:3000  (admin/admin)"
	@echo "OTel Collector: http://localhost:4318 (OTLP http)"

.PHONY: down
down: ## Stop the local stack
	$(COMPOSE) down

.PHONY: logs
logs: ## Tail logs from all containers
	$(COMPOSE) logs -f --tail=200

.PHONY: ps
ps: ## Show container status
	$(COMPOSE) ps

# ---------------------------------------------------------------------
# Demo: Break My Production Environment
# ---------------------------------------------------------------------
.PHONY: demo-500
demo-500: ## Inject HTTP 500s into payments-api (simulate SEV-1)
	scripts/fault-inject.sh http-500 100

.PHONY: demo-latency
demo-latency: ## Inject latency into payments-api
	scripts/fault-inject.sh inject-latency 100 latencyMs=3000

.PHONY: demo-cpu
demo-cpu: ## Exhaust CPU in payments-api
	scripts/fault-inject.sh cpu-exhaustion 100 cpuSeconds=10

.PHONY: demo-break-dep
demo-break-dep: ## Break the orders-api dependency
	scripts/fault-inject.sh break-dependency 100

.PHONY: demo-cert
demo-cert: ## Simulate an expired certificate failure
	scripts/fault-inject.sh expired-certificate 100

.PHONY: demo-reset
demo-reset: ## Clear all injected faults
	curl -s -X POST http://localhost:8081/faults/reset | jq .

.PHONY: demo-kill
demo-kill: ## Kill a pod (k8s) or kill the process (compose)
	scripts/kill-pod.sh payments-api

.PHONY: demo-rollback
demo-rollback: ## Trigger rollback on the active incident via the incident engine
	curl -s -X POST http://localhost:8083/incidents/$$(curl -s http://localhost:8083/incidents/active | jq -r '.[0].id')/rollback | jq .

# ---------------------------------------------------------------------
# Kind (local Kubernetes)
# ---------------------------------------------------------------------
.PHONY: kind-up
kind-up: ## Create a Kind cluster
	kind create cluster --config scripts/kind/kind-config.yaml --name sre-platform

.PHONY: kind-down
kind-down: ## Delete the Kind cluster
	kind delete cluster --name sre-platform

.PHONY: kind-apply
kind-apply: ## Deploy everything to Kind
	kubectl apply -k kubernetes/overlays/kind

.PHONY: kind-portforward
kind-portforward: ## Port-forward services to localhost
	kubectl -n sre-platform port-forward svc/grafana 3000:3000 &
	kubectl -n sre-platform port-forward svc/payments-api 8081:8080 &
	kubectl -n sre-platform port-forward svc/incident-engine 8083:8080 &

# ---------------------------------------------------------------------
# CI/CD (local mirror of GitHub Actions)
# ---------------------------------------------------------------------
.PHONY: ci
ci: ## Run the local CI pipeline (tests + SAST + scans)
	scripts/ci-local.sh

.PHONY: scan
scan: ## Run security scans against local sources
	scripts/ci-local.sh scan-only
