# Resource-safe local development

The Windows workstation must remain responsive during agent work.

## Agent rule

Agents do not run builds, tests, dependency installs, linting, typechecking, dev servers or automated browser/GUI verification locally on Windows.

That includes Maven, `npm ci` / `npm install`, Vitest, Vite builds, ESLint, TypeScript checks, Playwright/Cypress/Electron and the repository safe wrappers when used for ordinary verification.

The safe wrappers remain in the repository only for a future explicitly user-requested diagnostic. They are not part of the default agent development cycle.

Local work is limited to low-load source and Git operations: inspect/edit files, diff/status/log, commit and push.

## Verification

Push the implementation and let GitHub Actions perform automated verification. The authoritative full-suite gate is the remote root build:

`./mvnw clean verify`

If CI fails, inspect the remote logs, patch locally without executing the failing workload, push, and rerun CI.

The root cause of previous workstation lock-ups is not proven. RAM/commit/page-file pressure and GPU/VRAM pressure are plausible contributors, so no deliberate local stress testing should be used to distinguish them.
