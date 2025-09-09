# Test & TDD Strategy

Scope
- Applies to Android client and Node server.

Layers
- Unit: Pure functions/viewmodels/repos with fakes; tools: JUnit, MockK, coroutines-test, Vitest.
- Integration: Android repos with MockWebServer; Server Fastify with in-memory stores/ALTCHA bypass.
- DAO: Room DAOs (when added) with in-memory db; use TestCoroutineScheduler.
- UI Snapshot: Compose components via Paparazzi; determinism via fonts and stable sizes.
- UI Behavior: Compose UI tests for critical flows (smoke path).
- Perf smoke: Startup and one hot path timing in CI (later).

TDD workflow
- Red: write a small failing test for behavior.
- Green: implement minimal code to pass.
- Refactor: clean with coverage guards and detekt/lint.

Fixtures & builders
- Prefer small test data builders per module.
- Keep hermetic: avoid network/clock randomness; inject clocks and IO.

CI mapping
- Unit + integration (Android and Server) run in PR.
- Snapshot tests run headless with Paparazzi.
- Lint/Detekt/Coverage thresholds block PR.

Failure triage
- Hermetic defaults: tests run with UTC timezone and UTF-8 encoding to remove environment variance.
- CI auto-retry: Gradle test-retry runs a single retry only on CI; if a test passes on retry, build stays green but the triage report will flag it.
- Re-run locally with --tests Class#method to reproduce; if flaky, quarantine with @Ignore and open a Jira ticket.
- CI uploads artifacts: JUnit XML, triage summary (build/reports/triage/summary.md), and failure list (failures.txt).
