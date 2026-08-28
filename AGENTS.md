# Codex repository instructions

## Resource safety

Keeping the Windows workstation responsive is a hard requirement for all local agent work in this repository. Resource limits are product-development infrastructure, not optional tuning.

- Never run memory-intensive build or test commands in parallel.
- In particular, do not overlap Maven, `npm ci`, frontend tests, frontend builds, linting, or typechecking with each other.
- On Windows, run Maven through `./scripts/mvn-safe.cmd` and standalone npm commands through `./scripts/npm-safe.cmd`. These launchers invoke the repository PowerShell guards with a process-local execution-policy bypass, lower process priority, restrict the process tree to at most two logical CPUs, and apply the repository memory limits.
- Do not call the underlying `.ps1` files directly unless needed for debugging the wrappers themselves.
- Do not bypass the repository npm scripts with direct `npx`, direct Vitest/Vite/TypeScript/ESLint binaries, custom `NODE_OPTIONS`, or higher worker counts.
- Do not start watch mode, a Vite dev server, or other long-running development processes unless the current task explicitly requires them. Terminate such processes as soon as the check is complete.
- During implementation, prefer the narrowest relevant backend or frontend tests. Run the full root verification only once after the implementation is otherwise complete.
- Do not run `npm ci` repeatedly unless dependencies or the lockfile changed, or the installation is demonstrably invalid.
- Respect the repository's Node/Vitest/Maven heap and concurrency limits. Do not raise them, increase CPU affinity, or enable parallel Maven builds unless the user explicitly asks for that.
- If a test or build fails because a configured resource limit is too small, first narrow the test scope and investigate the concrete failure. Do not silently raise a limit just to make the command pass.
- If Windows becomes visibly memory- or CPU-constrained despite these limits, stop the heavy command and report it rather than continuing with further full-suite attempts.

The frontend resource contract is intentionally conservative: Node lifecycle processes are capped, Vitest is serial at both file and `test.concurrent` level, and spawned tooling inherits reduced CPU/process priority where supported.

## Required final verification

Unless the task explicitly specifies a different verification contract, the single authoritative full verification is:

- Windows local/Codex: `./scripts/mvn-safe.cmd clean verify`
- CI or non-Windows: `./mvnw clean verify`

The root Maven build already executes frontend install, tests, build, lint and typecheck. Do not redundantly run the complete frontend verification sequence immediately before or after the root build unless a concrete failure requires isolation.
