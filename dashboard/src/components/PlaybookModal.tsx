import type { Playbook } from '../types';

interface PlaybookModalProps {
  playbook: Playbook;
  onClose: () => void;
}

export function PlaybookModal({ playbook, onClose }: PlaybookModalProps) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h3>Incident Playbook — {playbook.incidentId}</h3>
            <p className="muted small">
              {playbook.service} · {playbook.severity} · {playbook.rootCause}
            </p>
          </div>
          <button className="btn btn-ghost" onClick={onClose}>✕</button>
        </div>
        <div className="phases">
          {playbook.phases.map((p) => (
            <div key={p.title} className="phase">
              <div className="phase-title">{p.title}</div>
              <ul>
                {p.steps.map((s, idx) => (
                  <li key={idx}>{s}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="modal-foot muted small">
          Reference: {playbook.awsReference}
        </div>
      </div>
    </div>
  );
}
