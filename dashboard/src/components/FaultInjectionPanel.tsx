import { api } from '../api';
import type { ActiveFault } from '../types';

interface FaultInjectionPanelProps {
  faults: ActiveFault[];
  setFaults: (f: ActiveFault[]) => void;
}

interface FaultDefinition {
  id: string;
  label: string;
  hint: string;
  params: Record<string, string>;
  danger: boolean;
}

const FAULTS: FaultDefinition[] = [
  { id: 'http-500', label: 'HTTP 500s', hint: 'Return 500 to 100% of traffic', params: { rate: '100', durationSeconds: '300' }, danger: true },
  { id: 'inject-latency', label: 'Latency', hint: '+3s p95 latency', params: { rate: '100', latencyMs: '3000', durationSeconds: '300' }, danger: false },
  { id: 'cpu-exhaustion', label: 'CPU exhaust', hint: 'Busy-spin on requests', params: { rate: '100', cpuSeconds: '10', durationSeconds: '300' }, danger: false },
  { id: 'break-dependency', label: 'Break dependency', hint: 'orders-api 503 + circuit open', params: { rate: '100', durationSeconds: '300' }, danger: true },
  { id: 'expired-certificate', label: 'Expired cert', hint: 'Simulated TLS failure (526)', params: { rate: '100', durationSeconds: '300' }, danger: true },
];

export function FaultInjectionPanel({ faults, setFaults }: FaultInjectionPanelProps) {
  const active = new Set(faults.map((f) => f.fault));

  const refresh = () => void api.faults().then(setFaults).catch(() => {});
  const apply = async (def: FaultDefinition) => {
    if (active.has(def.id)) {
      await api.clearFault(def.id);
    } else {
      await api.injectFault(def.id, def.params);
    }
    refresh();
  };
  const resetAll = async () => {
    await api.resetFaults();
    refresh();
  };

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Break My Production Environment</h2>
        <button className="btn btn-ghost" onClick={resetAll} disabled={!active.size}>
          reset all
        </button>
      </div>
      <p className="muted">
        Inject a fault into <code>payments-api</code>. The incident engine and
        Prometheus will detect the degradation within seconds.
      </p>
      <div className="fault-grid">
        {FAULTS.map((def) => {
          const on = active.has(def.id);
          return (
            <button
              key={def.id}
              className={`fault-btn ${on ? 'on' : ''} ${def.danger ? 'danger' : ''}`}
              onClick={() => void apply(def)}
              title={def.hint}
            >
              <span className="fault-label">{on ? '✓' : '⚡'} {def.label}</span>
              <span className="fault-hint">{def.hint}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
