import type {
  ActiveFault,
  Incident,
  Playbook,
  RollbackResult,
  ServiceSnapshot,
} from './types';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init);
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`${init?.method ?? 'GET'} ${path} -> ${res.status} ${body}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  activeIncidents: () => request<Incident[]>('/api/incidents/active'),
  allIncidents: () => request<Incident[]>('/api/incidents'),
  services: () => request<ServiceSnapshot[]>('/api/services'),
  playbook: (id: string) => request<Playbook>(`/api/incidents/${id}/playbook`),
  ack: (id: string) =>
    request<Incident>(`/api/incidents/${id}/ack`, { method: 'POST' }),
  resolve: (id: string) =>
    request<Incident>(`/api/incidents/${id}/resolve`, { method: 'POST' }),
  rollback: (id: string) =>
    request<RollbackResult>(`/api/incidents/${id}/rollback`, { method: 'POST' }),

  faults: () => request<ActiveFault[]>('/api/faults'),
  injectFault: (id: string, params: Record<string, string>) => {
    const qs = new URLSearchParams(params).toString();
    return request(`/api/faults/${id}/inject?${qs}`, { method: 'POST' });
  },
  clearFault: (id: string) =>
    request(`/api/faults/${id}/clear`, { method: 'POST' }),
  resetFaults: () => request('/api/faults/reset', { method: 'POST' }),

  // Traffic generator (drives the SLO metrics so incidents can be detected).
  generateTraffic: (n: number) => {
    const url = `/api/payments?gen=${Math.random().toString(36).slice(2)}`;
    for (let i = 0; i < n; i++) {
      void fetch(url).catch(() => {});
    }
  },
};
