export type Severity = 'SEV_1' | 'SEV_2';
export type IncidentStatus = 'OPEN' | 'ACK' | 'RESOLVED';

export interface Incident {
  id: string;
  service: string;
  severity: Severity;
  status: IncidentStatus;
  detectedBy: string;
  signal: string;
  rootCause: string;
  recommendedAction: string;
  errorRate: number;
  p95LatencyMs: number;
  healthyPods: number;
  replicas: number;
  activeFaults: string[];
  openedAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface ServiceSnapshot {
  name: string;
  version: string;
  health: string;
  status: string;
  errorRate: number;
  p95LatencyMs: number;
  requestCount: number;
  activeFaults: string[];
  observedAt: string;
}

export interface ActiveFault {
  fault: string;
  rate: number;
  latencyMs: number;
  cpuSeconds: number;
  since: string;
  expiresAt: string | null;
  triggeredBy: string;
}

export interface PlaybookPhase {
  title: string;
  steps: string[];
}

export interface Playbook {
  incidentId: string;
  service: string;
  severity: string;
  rootCause: string;
  phases: PlaybookPhase[];
  awsReference: string;
}

export interface RollbackResult {
  incidentId: string;
  action: string;
  status: 'SUCCESS' | 'PARTIAL';
  executed: string;
  productionCommand: string;
  notes: string[];
}
