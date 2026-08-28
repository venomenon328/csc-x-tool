import { spawnSync } from 'node:child_process'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const [tool, ...args] = process.argv.slice(2)

const tools = {
  eslint: path.join(frontendRoot, 'node_modules', 'eslint', 'bin', 'eslint.js'),
  tsc: path.join(frontendRoot, 'node_modules', 'typescript', 'bin', 'tsc'),
  vite: path.join(frontendRoot, 'node_modules', 'vite', 'bin', 'vite.js'),
  vitest: path.join(frontendRoot, 'node_modules', 'vitest', 'vitest.mjs'),
}

if (!(tool in tools)) {
  console.error(`Unknown resource-limited frontend tool: ${tool ?? '<missing>'}`)
  process.exit(2)
}

try {
  os.setPriority(0, os.constants.priority.PRIORITY_BELOW_NORMAL)
} catch (error) {
  console.warn(`Could not lower frontend process priority: ${error instanceof Error ? error.message : String(error)}`)
}

const env = {
  ...process.env,
  NODE_OPTIONS: '--max-old-space-size=512',
  GOMAXPROCS: '2',
  UV_THREADPOOL_SIZE: '2',
  VITEST_MAX_WORKERS: '1',
}

const result = spawnSync(process.execPath, [tools[tool], ...args], {
  cwd: frontendRoot,
  env,
  stdio: 'inherit',
})

if (result.error) {
  console.error(result.error)
  process.exit(1)
}

process.exit(result.status ?? 1)
