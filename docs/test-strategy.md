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
- Re-run failed tests; if flaky, quarantine with @Ignore and open issue.
- Keep test logs and artifacts uploaded in CI.
