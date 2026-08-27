# Codex repository instructions

## Resource safety

Keeping the Windows workstation responsive is a hard requirement for local agent work in this repository.

- Never run memory-intensive build or test commands in parallel.
- In particular, do not overlap `./mvnw clean verify`, `npm ci`, `npm test`, `npm run build`, `npm run lint`, or `npm run typecheck` with each other.
- Do not start watch mode, a Vite dev server, or other long-running development processes unless the current task explicitly requires them. Terminate such processes as soon as the check is complete.
- During implementation, prefer the narrowest relevant backend or frontend tests. Run the full `./mvnw clean verify` only as the final verification step, not after every small edit.
- Respect the repository's Node/Vitest memory and worker limits. Do not override `NODE_OPTIONS`, Vitest `maxWorkers`, or `fileParallelism` to increase resource use unless the user explicitly asks for that.
- If a test or build fails because the configured memory limit is too small, do not silently raise the limit. First narrow the test scope; if the full required build still cannot pass, report the concrete failing command and memory error.

## Required final verification

Unless the task explicitly specifies a different verification contract, complete implementation work with:

`./mvnw clean verify`

Run that command by itself, with no other heavy build or test process active.
