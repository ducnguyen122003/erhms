import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const apiTarget = process.env.API_TARGET || 'http://localhost:8080';
const proxy = { '/api': apiTarget, '/actuator': apiTarget };

export default defineConfig({
  plugins: [react()],
  server: { port: 8082, strictPort: true, proxy },
  preview: { port: 8082, strictPort: true, proxy }
});
