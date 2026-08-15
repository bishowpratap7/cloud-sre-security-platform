import { useEffect, useMemo, useState } from 'react';
import { api } from './api';
import type { ActiveFault, Incident, Playbook, ServiceSnapshot } from './types';
import { Header } from './components/Header';
import { FaultInjectionPanel } from './components/FaultInjectionPanel';
import { ServiceStatus } from './components/ServiceStatus';
import { IncidentCard } from './components/IncidentCard';
import { IncidentList } from './components/IncidentList';
import { PlaybookModal } from './components/PlaybookModal';

const POLL_MS = 3000;

export default function App() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [history, setHistory] = useState<Incident[]>([]);
  const [services, setServices] = useState<ServiceSnapshot[]>([]);
  const [faults, setFaults] = useState<ActiveFault[]>([]);
  const [playbook, setPlaybook] = useState<Playbook | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [traffic, setTraffic] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      try {
        const [active, all, svcs, f] = await Promise.all([
          api.activeIncidents(),
          api.allIncidents(),
          api.services(),
          api.faults(),
        ]);
        if (cancelled) return;
        setIncidents(active);
        setHistory(all);
        setServices(svcs);
        setFaults(f);
        setError(null);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      }
    };
    void tick();
    const timer = setInterval(tick, POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (!traffic) return;
    const timer = setInterval(() => api.generateTraffic(8), POLL_MS);
    return () => clearInterval(timer);
  }, [traffic]);

  const latest = incidents[0] ?? null;
  const totalFaults = useMemo(
    () => faults.reduce((n, f) => n + (f.rate > 0 ? 1 : 0), 0),
    [faults],
  );

  return (
    <div className="app">
      <Header
        error={error}
        traffic={traffic}
        onToggleTraffic={() => setTraffic((v) => !v)}
        faultCount={totalFaults}
        incidentCount={incidents.length}
      />

      {latest && (
        <IncidentCard
          incident={latest}
          onOpenPlaybook={() => {
            void api.playbook(latest.id).then(setPlaybook).catch(setError);
          }}
          onRollback={async () => {
            const result = await api.rollback(latest.id);
            setError(null);
            alert(
              `${result.action} ${result.status}\n\n${result.notes.join('\n')}`,
            );
          }}
        />
      )}

      <div className="grid">
        <FaultInjectionPanel faults={faults} setFaults={setFaults} />
        <ServiceStatus services={services} />
      </div>

      <IncidentList incidents={history} />

      {playbook && (
        <PlaybookModal playbook={playbook} onClose={() => setPlaybook(null)} />
      )}
    </div>
  );
}
