# Resource-safe local development

Local agent work on Windows must keep the workstation responsive.

- Use `./scripts/mvn-safe.cmd` for Maven commands.
- Use `./scripts/npm-safe.cmd` for standalone npm commands.
- The `.cmd` launchers call the PowerShell guards with a process-local execution-policy bypass, so no global PowerShell policy change is required.
- The guards lower process priority, restrict descendants to at most two logical CPUs, and apply the repository memory limits.
- The repository additionally caps Maven/Node heaps and keeps Vitest fully serial.
- Do not increase these limits without an explicit decision based on a reproducible failing build.
- Never overlap Maven, npm install, frontend tests/build/lint/typecheck, browser automation, dev servers, or other heavy processes.
- Automated verification must not launch a hardware-accelerated browser or GUI unless the task explicitly requires it. A previous workstation lock-up had no proven root cause; possible GPU/VRAM pressure is therefore treated as a risk alongside CPU/RAM pressure.
- Before a heavy check, terminate agent-started watchers, dev servers, stale Node/Java processes and agent-started browsers from earlier steps.
- Prefer narrow targeted checks while implementing. Attempt the full Windows root build at most once, only after targeted checks pass.
- If responsiveness degrades materially or CPU/RAM/GPU/VRAM pressure becomes visible, abort the heavy command immediately and do not retry it in the same task.
- After such an abort, keep local verification targeted and require the GitHub Actions root build (`./mvnw clean verify`) to succeed before treating the implementation as fully verified. Document that the local Windows full run was aborted or skipped.
