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
    // Keep local/Codex test runs deliberately serial. The jsdom suites can otherwise
    // spawn enough Node workers to starve the desktop of memory on Windows.
    maxWorkers: 1,
    fileParallelism: false,
    // The application shell deliberately imports all working areas; allow Windows/CI enough time
    // for the first jsdom interaction without weakening any assertion or retry logic.
    testTimeout: 10_000,
  },
})
