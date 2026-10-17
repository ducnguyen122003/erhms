import { readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';

const root = fileURLToPath(new URL('.', import.meta.url));
const apiTarget = process.env.API_TARGET || 'http://localhost:8080';
const proxy = { '/api': apiTarget };
const pages = readdirSync(root)
  .filter((file) => file.endsWith('.html'))
  .map((file) => resolve(root, file));

export default defineConfig({
  server: { port: 8081, strictPort: true, proxy },
  preview: { port: 8081, strictPort: true, proxy },
  build: { rollupOptions: { input: pages } }
});
