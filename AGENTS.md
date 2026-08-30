# Codex repository instructions

## Resource safety

Keeping the Windows workstation responsive is a hard requirement for all local agent work in this repository.

Previous local build/test runs have repeatedly made the workstation effectively unusable despite conservative CPU, heap and worker limits. The exact bottleneck is not proven; RAM/commit pressure, page-file activity and GPU/VRAM pressure are all plausible contributors. Do not diagnose this by deliberately stressing the workstation.

### Hard rule: no local agent builds or tests

Unless the user explicitly reverses this rule for one concrete diagnostic command, agents must **not execute build, test, dependency-install, lint or typecheck workloads on the Windows workstation**.

This prohibition includes, but is not limited to:

- Maven build/test/verify/package commands, including `./scripts/mvn-safe.cmd`;
- `npm ci` / `npm install`;
- frontend tests, Vitest, Vite builds, ESLint and TypeScript typechecking;
- direct `npx` or direct tool-binary execution;
- Playwright, Cypress, Electron, WebView or automated browser/GUI verification;
- watch mode, dev servers and other long-running development processes used for automated verification.

The existing safe wrappers remain available for a future **explicitly user-requested diagnostic**, but they are not part of the normal agent workflow anymore.

Local agent work should be limited to low-load operations such as reading/editing files, source inspection, Git status/diff/log operations, commits and pushes. Do not start Java/Node application processes merely to verify an implementation.

### Verification belongs on GitHub

All automated verification must run on GitHub Actions or another remote CI runner.

- The authoritative full verification is the GitHub Actions root build executing `./mvnw clean verify`.
- During implementation, commit and push coherent increments and use CI feedback instead of running targeted tests locally.
- If narrower remote feedback is needed, prefer an existing targeted GitHub Actions workflow. It is acceptable to improve CI workflow ergonomics when that is genuinely useful, but do not weaken test coverage or change product behavior merely to make verification cheaper.
- Do not claim an implementation is fully verified until the required GitHub Actions checks for the current head commit are green.
- If CI fails, inspect the remote logs, patch locally without executing the failing workload, push again, and let CI rerun.

### Browser/manual acceptance

Automated browser acceptance on the Windows workstation is prohibited. If a change requires genuine browser or Windows acceptance that CI cannot provide, leave it as an explicit manual check for the user rather than launching hardware-accelerated tooling automatically.

## Required final verification

Unless a task explicitly defines additional remote checks, the required full-suite gate is:

- GitHub Actions / non-Windows CI: `./mvnw clean verify`

No local Windows build or test command is required or permitted for normal agent implementation work.
