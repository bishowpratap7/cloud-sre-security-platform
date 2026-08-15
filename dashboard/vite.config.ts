import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev proxy: mirrors the nginx routing used in production so the dashboard
// always talks to same-origin /api/* paths.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/incidents': {
        target: 'http://localhost:8083',
        rewrite: (path) => path.replace(/^\/api\/incidents/, '/incidents'),
      },
      '/api/services': {
        target: 'http://localhost:8083',
        rewrite: (path) => path.replace(/^\/api\/services/, '/services'),
      },
      '/api/faults': {
        target: 'http://localhost:8081',
        rewrite: (path) => path.replace(/^\/api\/faults/, '/faults'),
      },
      '/api/payments': {
        target: 'http://localhost:8081',
        rewrite: (path) => path.replace(/^\/api\/payments/, '/payments'),
      },
    },
  },
});
