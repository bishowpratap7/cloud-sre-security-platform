import type { Incident } from '../types';

interface IncidentListProps {
  incidents: Incident[];
}

export function IncidentList({ incidents }: IncidentListProps) {
  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Incident History</h2>
      </div>
      {incidents.length === 0 && <div className="muted">No incidents recorded.</div>}
      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Service</th>
            <th>Severity</th>
            <th>Status</th>
            <th>Root cause</th>
            <th>Error rate</th>
            <th>Opened</th>
          </tr>
        </thead>
        <tbody>
          {incidents.map((i) => (
            <tr key={i.id}>
              <td className="mono">{i.id}</td>
              <td>{i.service}</td>
              <td>
                <span className={`severity-badge ${i.severity}`}>
                  {i.severity === 'SEV_1' ? 'SEV-1' : 'SEV-2'}
                </span>
              </td>
              <td>
                <span className={`status-badge ${i.status.toLowerCase()}`}>{i.status}</span>
              </td>
              <td className="root-cause">{i.rootCause}</td>
              <td>{i.errorRate.toFixed(1)}%</td>
              <td className="muted small">
                {new Date(i.openedAt).toLocaleTimeString()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
