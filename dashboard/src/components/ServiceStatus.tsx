import type { ServiceSnapshot } from '../types';

interface ServiceStatusProps {
  services: ServiceSnapshot[];
}

export function ServiceStatus({ services }: ServiceStatusProps) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Service Health</h2>
      </div>
      <p className="muted">Liveness / readiness probes + SLO metrics, refreshed every 3s.</p>
      <div className="service-list">
        {services.length === 0 && <div className="muted">No services monitored.</div>}
        {services.map((s) => {
          const degraded = s.status !== 'HEALTHY';
          return (
            <div key={s.name} className={`service-row ${degraded ? 'degraded' : ''}`}>
              <div className="service-left">
                <span className={`dot ${degraded ? 'dot-bad' : 'dot-good'}`} />
                <div>
                  <div className="service-name">{s.name}</div>
                  <div className="muted small">
                    v{s.version} · {s.health} · probe {s.health === 'UP' ? 'ok' : 'failing'}
                  </div>
                </div>
              </div>
              <div className="service-metrics">
                <div className={`metric ${s.errorRate >= 20 ? 'bad' : ''}`}>
                  <span className="metric-label">error</span>
                  <span className="metric-value">{s.errorRate.toFixed(1)}%</span>
                </div>
                <div className={`metric ${s.p95LatencyMs >= 1500 ? 'bad' : ''}`}>
                  <span className="metric-label">p95</span>
                  <span className="metric-value">{s.p95LatencyMs.toFixed(0)}ms</span>
                </div>
                <div className="metric">
                  <span className="metric-label">req</span>
                  <span className="metric-value">{s.requestCount}</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
