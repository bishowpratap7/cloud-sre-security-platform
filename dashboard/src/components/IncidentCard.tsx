import { useState } from 'react';
import type { Incident } from '../types';

interface IncidentCardProps {
  incident: Incident;
  onOpenPlaybook: () => void;
  onRollback: () => void;
}

export function IncidentCard({ incident, onOpenPlaybook, onRollback }: IncidentCardProps) {
  const [view, setView] = useState<'logs' | 'trace' | null>(null);
  const severity = incident.severity === 'SEV_1' ? 'SEV-1' : 'SEV-2';

  return (
    <>
      <section className={`incident-card sev-${incident.severity}`}>
        <div className="incident-banner">
          <span className="incident-label">⚠ INCIDENT DETECTED</span>
          <span className={`severity-badge ${incident.severity}`}>{severity}</span>
          <span className={`status-badge ${incident.status.toLowerCase()}`}>{incident.status}</span>
        </div>

        <div className="incident-body">
          <div className="incident-facts">
            <div className="fact">
              <span className="fact-label">Service</span>
              <span className="fact-value">{incident.service}</span>
            </div>
            <div className="fact">
              <span className="fact-label">Error rate</span>
              <span className="fact-value">{incident.errorRate.toFixed(1)}%</span>
            </div>
            <div className="fact">
              <span className="fact-label">Latency p95</span>
              <span className="fact-value">{incident.p95LatencyMs.toFixed(0)} ms</span>
            </div>
            <div className="fact">
              <span className="fact-label">Healthy pods</span>
              <span className="fact-value">
                {incident.healthyPods}/{incident.replicas}
              </span>
            </div>
            <div className="fact">
              <span className="fact-label">Detected by</span>
              <span className="fact-value small">{incident.detectedBy}</span>
            </div>
            <div className="fact">
              <span className="fact-label">Signal</span>
              <span className="fact-value small">{incident.signal}</span>
            </div>
          </div>

          <div className="incident-detail">
            <div className="detail-block">
              <span className="detail-label">Root cause</span>
              <p>{incident.rootCause}</p>
            </div>
            <div className="detail-block">
              <span className="detail-label">Recommended action</span>
              <p>{incident.recommendedAction}</p>
            </div>
          </div>
        </div>

        <div className="incident-actions">
          <button className="btn btn-danger" onClick={onRollback}>↺ Rollback</button>
          <button className="btn" onClick={() => setView('logs')}>View Logs</button>
          <button className="btn" onClick={() => setView('trace')}>View Trace</button>
          <button className="btn" onClick={onOpenPlaybook}>Incident Playbook</button>
        </div>
      </section>

      {view && (
        <div className="modal-overlay" onClick={() => setView(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>{view === 'logs' ? 'Log Stream' : 'Distributed Trace'}</h3>
              <button className="btn btn-ghost" onClick={() => setView(null)}>✕</button>
            </div>
            <pre className="code-block">
              {view === 'logs'
                ? buildLogs(incident)
                : buildTrace(incident)}
            </pre>
          </div>
        </div>
      )}
    </>
  );
}

function buildLogs(i: Incident): string {
  const t = new Date(i.updatedAt).toISOString();
  return [
    `${t} ERROR c.s.payments.PaymentController : Request failed`,
    `${t} WARN  c.s.payments.api.OrdersClient  : orders-api call failed: 500`,
    `${t} ERROR o.s.web.servlet.DispatcherServlet: Failed to complete request (trace=${traceId(i)})`,
    `${t} ERROR c.s.payments.api.PaymentController : Dependency error returned to client`,
    `--- streamed by Loki / OpenSearch, source=${i.service}-*, query={service="${i.service}"} ---`,
  ].join('\n');
}

function buildTrace(i: Incident): string {
  return [
    `{ span "GET /payments/${i.service}" · traceId="${traceId(i)}" · spanId="s${Math.floor(Math.random() * 90000) + 10000}" }`,
    `  status        = ERROR (${i.activeFaults.join(', ') || 'degraded'})`,
    `  duration      = ${i.p95LatencyMs.toFixed(0)} ms`,
    `  attributes    = service.name: ${i.service}, deployment.version: ${''}, http.response.status_code: 5xx`,
    `  resource      = k8s.namespace.name: sre-platform, k8s.pod.name: ${i.service}-xxxxx`,
    `  exporter      = OTLP/http → opentelemetry-collector → Prometheus/Grafana`,
    ``,
    `~ exported by opentelemetry-javaagent (${i.detectedBy.split(' + ')[1]})`,
  ].join('\n');
}

function traceId(i: Incident): string {
  const seed = i.id.replace(/\D/g, '').padStart(16, '0').slice(-16);
  return `${seed.slice(0, 8)}-${seed.slice(8)}-${seed}${seed.slice(0, 8)}`;
}
