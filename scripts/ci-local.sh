#!/usr/bin/env bash
# Run the full local quality + security gate (the same checks as CI) on a
# developer machine. Requires: java, mvn, node, npm, and optional: trivy.
set -euo pipefail

echo "====[1/5] Backend tests (Java 21 + Maven) ===="
mvn -f services/pom.xml -B -ntp clean verify

echo "====[2/5] Dashboard build (Vite + TS) ===="
(
  cd dashboard
  npm ci
  npm run build
)

echo "====[3/5] IaC / manifest sanity (yaml parse) ===="
python - <<'PY'
import glob, yaml
files = glob.glob('kubernetes/**/*.yaml', recursive=True)
for f in files:
    for doc in yaml.safe_load_all(open(f, encoding='utf-8')):
        assert doc is None or isinstance(doc, dict), f
print(f"OK: {len(files)} k8s yaml files parse")
PY

if command -v trivy >/dev/null 2>&1; then
  echo "====[4/5] Trivy IaC scan ===="
  trivy config --scanners misconfig kubernetes/ terraform/ docker-compose.yml

  echo "====[5/5] Trivy filesystem scan (deps + secrets) ===="
  trivy fs --scanners vuln,secret --severity HIGH,CRITICAL --exit-code 1 .
else
  echo "====[4/5 + 5/5] trivy not installed — skipping image/config scans ===="
  echo "       Install: https://aquasecurity.github.io/trivy/"
fi

echo "Local CI gate passed."
