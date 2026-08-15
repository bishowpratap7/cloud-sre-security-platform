#!/usr/bin/env bash
# Kill one random pod of a deployment to demonstrate self-healing / rollback.
#
# Usage:
#   ./scripts/kill-pod.sh <deployment> [namespace]
#
# Examples:
#   ./scripts/kill-pod.sh payments-api
#   ./scripts/kill-pod.sh orders-api
set -euo pipefail

deployment="${1:?usage: kill-pod.sh <deployment> [namespace]}"
namespace="${2:-sre-platform}"

echo ">> Deleting one pod of deployment '${deployment}' in '${namespace}'"
pod="$(kubectl -n "${namespace}" get pods -l "app=${deployment}" -o jsonpath='{.items[0].metadata.name}')"
echo ">> Deleting pod ${pod}"
kubectl -n "${namespace}" delete pod "${pod}" --wait=false

echo ">> Watching self-heal (ReplicaSet controller recreates the pod)..."
kubectl -n "${namespace}" rollout status "deployment/${deployment}" --timeout=120s
echo ">> New pods:"
kubectl -n "${namespace}" get pods -l "app=${deployment}"
