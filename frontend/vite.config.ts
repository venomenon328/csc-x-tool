import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    globals: true,
    // Local/Codex runs are deliberately serial. A single fork is kept for compatibility,
    // while npm scripts additionally cap both the runner and its worker processes.
    pool: 'forks',
    maxWorkers: 1,
    fileParallelism: false,
    maxConcurrency: 1,
    // The application shell deliberately imports all working areas; allow Windows/CI enough time
    // for the first jsdom interaction without weakening any assertion or retry logic.
    testTimeout: 10_000,
  },
})
