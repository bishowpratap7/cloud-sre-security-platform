#!/usr/bin/env bash
# Inject a fault into payments-api (local compose stack or kind cluster).
#
# Usage:
#   ./scripts/fault-inject.sh <fault-id> [rate] [extra-params...]
#
# Examples:
#   ./scripts/fault-inject.sh http-500 100
#   ./scripts/fault-inject.sh inject-latency 100 latencyMs=3000
#   ./scripts/fault-inject.sh cpu-exhaustion 100 cpuSeconds=10
#   ./scripts/fault-inject.sh break-dependency 100
#   ./scripts/fault-inject.sh expired-certificate 100
#
# Fault ids: http-500, inject-latency, cpu-exhaustion,
#            break-dependency, expired-certificate
set -euo pipefail

BASE_URL="${FAULT_BASE_URL:-http://localhost:8081}"

fault_id="${1:?usage: fault-inject.sh <fault-id> [rate] [params...]}"
rate="${2:-100}"
shift 2 || true

params="rate=${rate}"
for p in "$@"; do
  params="${params}&${p}"
done

echo ">> Injecting '${fault_id}' (${params}) into payments-api @ ${BASE_URL}"
curl -sf -X POST "${BASE_URL}/faults/${fault_id}/inject?${params}" | jq .
echo ">> Fault injected. Watch the dashboard / incident-engine:"
echo "   curl -s ${BASE_URL}/faults | jq ."
