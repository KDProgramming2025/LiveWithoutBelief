# Live Without Belief

Early-stage Android + Node.js project exploring secular guidance and article consumption with private annotations.

## Status

| CI (Android) | Coverage | License |
|--------------|----------|---------|
| ![CI](https://github.com/KDProgramming2025/LiveWithoutBelief/actions/workflows/android.yml/badge.svg) | _Kover pending_ | Apache-2.0 |

## Modules
- `core` (model, common, domain)
- `data` (network, repo)
- `feature` (reader, etc.)
- `server` (Node backend placeholder)

## Quality
`./gradlew quality` runs: tests, detekt, spotlessCheck, coverage verify, dependencyGuard.

Coverage thresholds (line): overall 70%, core 80%, data 70%, feature 60%.

## Pre-commit Hook (optional)
```
git config core.hooksPath .githooks
```

## ADRs
See `docs/adr/` for architectural decisions (0004 covers static analysis & coverage).

## Smart Commits
Use: `LWB-19 #time 30m #comment message` to log work to Jira.

---
Work in progress; more docs forthcoming.