# Codex repository instructions

## Resource safety

Keeping the Windows workstation responsive is a hard requirement for all local agent work in this repository. Resource limits are product-development infrastructure, not optional tuning.

A previous full local verification made the workstation effectively unresponsive. The exact cause was not proven; GPU/VRAM pressure is a plausible contributor, but this is not established. Treat both CPU/RAM pressure and hardware-accelerated GUI/browser processes as potential risks. Do not diagnose the incident by deliberately stress-testing the machine.

- Never run memory-intensive build or test commands in parallel.
- In particular, do not overlap Maven, `npm ci`, frontend tests, frontend builds, linting, or typechecking with each other.
- On Windows, run Maven through `./scripts/mvn-safe.cmd` and standalone npm commands through `./scripts/npm-safe.cmd`. These launchers invoke the repository PowerShell guards with a process-local execution-policy bypass, lower process priority, restrict the process tree to at most two logical CPUs, and apply the repository memory limits.
- Do not call the underlying `.ps1` files directly unless needed for debugging the wrappers themselves.
- Do not bypass the repository npm scripts with direct `npx`, direct Vitest/Vite/TypeScript/ESLint binaries, custom `NODE_OPTIONS`, or higher worker counts.
- Do not start watch mode, a Vite dev server, or other long-running development processes unless the current task explicitly requires them. Terminate such processes as soon as the check is complete.
- Do not launch Vivaldi, Chrome, Edge, Electron, Playwright, Cypress, WebView-based tooling, or another hardware-accelerated browser/GUI merely for automated verification. Browser acceptance is manual unless the task explicitly requires browser automation and the user has accepted that resource risk.
- Before any heavy verification, ensure no agent-started dev server, watcher, stale Node process, stale Java process, or agent-started browser remains running from an earlier step.
- During implementation, prefer the narrowest relevant backend or frontend tests. Run the full root verification only once after the implementation is otherwise complete.
- Do not run `npm ci` repeatedly unless dependencies or the lockfile changed, or the installation is demonstrably invalid.
- Respect the repository's Node/Vitest/Maven heap and concurrency limits. Do not raise them, increase CPU affinity, or enable parallel Maven builds unless the user explicitly asks for that.
- If a test or build fails because a configured resource limit is too small, first narrow the test scope and investigate the concrete failure. Do not silently raise a limit just to make the command pass.
- If Windows becomes visibly memory-, CPU-, GPU- or VRAM-constrained, or desktop responsiveness degrades materially, stop the heavy command immediately. Do not retry that full command in the same task.
- If a local full Windows verification is aborted or skipped for resource safety, use targeted local checks only, push the branch, and require the GitHub Actions root build (`./mvnw clean verify`) to succeed before claiming verification. Report explicitly that the local Windows full run was not completed.

The frontend resource contract is intentionally conservative: Node lifecycle processes are capped, Vitest is serial at both file and `test.concurrent` level, and spawned tooling inherits reduced CPU/process priority where supported.

## Required final verification

Unless the task explicitly specifies a different verification contract, the authoritative full verification is:

- Windows local/Codex, when the workstation remains demonstrably responsive: `./scripts/mvn-safe.cmd clean verify`
- CI or non-Windows: `./mvnw clean verify`

A local Windows full run may be attempted at most once per implementation after targeted checks are green. If it causes or begins to cause resource pressure, abort it and do not retry; the green CI root build then becomes the required full-suite gate.

The root Maven build already executes frontend install, tests, build, lint and typecheck. Do not redundantly run the complete frontend verification sequence immediately before or after the root build unless a concrete failure requires isolation.
