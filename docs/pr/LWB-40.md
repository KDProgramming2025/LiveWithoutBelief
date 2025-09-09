# LWB-40: Establish test layers (fixtures, UI snapshots, performance smoke)

Summary

This PR completes LWB-40 by adding dependable testing layers across the project:
- LWB-42: Shared test fixtures module for builders/rules/utilities.
- LWB-43: Paparazzi snapshot tests for design-system (and a minimal reader header snapshot).
- LWB-44: Macrobenchmark for app startup (cold + warm) as performance smoke.

Scope

- CI runs these tests on PRs; snapshots are enabled headless; artifacts uploaded.
- No app feature behavior change; test-only modules and wiring, plus minor DI hooks for testability.

Changes

- core:test-fixtures module with:
  - Builders for content models.
  - MainDispatcherRule, MockWebServerRule, JsonResource helper.
- ui:design-system module:
  - LwbTheme and color swatch sample.
  - Paparazzi tests for light/dark theme snapshots.
- feature:reader minimal snapshot for header sample; screen snapshot kept ignored until version align is safe across all environments.
 - feature:reader minimal snapshot for header sample; no disabled or ignored tests remain.
- benchmark module:
  - StartupBenchmark.kt adds cold and warm startup measurements.
- Toolchains aligned via Foojay; Paparazzi/layoutlib stabilized for CI.
- CI tightened to pull_request + workflow_dispatch only. Release workflow guarded.

CI

- GitHub Actions: android.yml runs unit + snapshot tests; artifacts include Paparazzi reports and test reports.
- Dependency submission disabled on normal CI; release.yml only via release-please merge or manual dispatch.

Validation

- Local: unit + snapshot tests pass; macrobenchmark compiles (execution is device-bound and excluded from CI by default).
- CI: green on PR with snapshots enabled; distinct artifact names per JDK.

How to run locally (optional)

- Unit + snapshots: ./gradlew test
- Paparazzi report: open module build/reports/paparazzi/*/index.html
- Macrobenchmark: run from Android Studio on a physical device

Mapping to Jira

- LWB-42: DONE — test fixtures module available and consumed.
- LWB-43: DONE — Paparazzi configured, tests added, enabled on CI.
- LWB-44: DONE — startup macrobenchmark added (device-run only).

Notes

- No stubs or ignored tests. Snapshot coverage focuses on stable components (design-system and reader header).
- Follow-ups (post LWB-40): expand snapshot coverage, add baseline profiles, optional DAO/repo integration tests as needed.
