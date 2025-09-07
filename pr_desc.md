## Summary
Static analysis & coverage foundation.

## Key Changes
- Spotless + ktlint (SPDX headers)
- Detekt with SARIF + HTML outputs
- Overall coverage gate (70%)
- Dependency guard task
- `quality` aggregate task (tests, detekt, lintDebug, coverage, spotless, dependencyGuard)
- Pre-commit hook (.githooks)
- README, CODEOWNERS, commit/PR/issue templates
- ADR 0004 (layered coverage deferred; overall active)

## Deferred
- Layered per-layer coverage (DSL instability in Kover 0.9.0)
- Coverage badge automation
- SARIF ingestion (security scans) & secrets/dependency scans
- Paparazzi/UI coverage uplift

## Validation
`./gradlew quality` passes locally.

## Jira
LWB-19, LWB-24 time logged via Smart Commits.
