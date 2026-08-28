# Resource-safe local development

Local agent work on Windows must keep the workstation responsive.

- Use `./scripts/mvn-safe.cmd` for Maven commands.
- Use `./scripts/npm-safe.cmd` for standalone npm commands.
- The `.cmd` launchers call the PowerShell guards with a process-local execution-policy bypass, so no global PowerShell policy change is required.
- The guards lower process priority, restrict descendants to at most two logical CPUs, and apply the repository memory limits.
- The repository additionally caps Maven/Node heaps and keeps Vitest fully serial.
- Do not increase these limits without an explicit decision based on a reproducible failing build.
