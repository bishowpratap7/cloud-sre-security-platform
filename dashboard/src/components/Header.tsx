interface HeaderProps {
  error: string | null;
  traffic: boolean;
  onToggleTraffic: () => void;
  faultCount: number;
  incidentCount: number;
}

export function Header({
  error,
  traffic,
  onToggleTraffic,
  faultCount,
  incidentCount,
}: HeaderProps) {
  return (
    <header className="header">
      <div className="brand">
        <span className="logo">⌁</span>
        <div>
          <h1>SRE Platform</h1>
          <p className="subtitle">Reliability · Observability · Security</p>
        </div>
      </div>

      <div className="status-pills">
        {error && <span className="pill pill-error" title={error}>offline</span>}
        <span className={`pill ${faultCount > 0 ? 'pill-warn' : 'pill-ok'}`}>
          faults: {faultCount}
        </span>
        <span className={`pill ${incidentCount > 0 ? 'pill-danger' : 'pill-ok'}`}>
          incidents: {incidentCount}
        </span>
        <button
          className={`btn btn-ghost ${traffic ? 'active' : ''}`}
          onClick={onToggleTraffic}
          title="Generate synthetic traffic so SLO metrics move"
        >
          {traffic ? '⏸ traffic on' : '▶ traffic off'}
        </button>
      </div>
    </header>
  );
}
