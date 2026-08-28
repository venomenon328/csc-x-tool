# Resource-safe local development

Local agent work on Windows must keep the workstation responsive.

- Use `./scripts/mvn-safe.ps1` for Maven commands.
- Use `./scripts/npm-safe.ps1` for standalone npm commands.
- The repository additionally caps Maven/Node heaps and keeps Vitest fully serial.
- Do not increase these limits without an explicit decision based on a reproducible failing build.
