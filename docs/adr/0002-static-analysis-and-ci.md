# ADR 0002: Static Analysis & CI

Date: 2025-09-12

## Status
Accepted

## Context
We require consistent code style and automated quality gates.

## Decision
- Spotless (ktlint), Detekt, Android Lint enabled
- GitHub Actions CI runs build, tests, lint, detekt, dependency review
- Coverage via Kover (planned) and Paparazzi for snapshot tests

## Consequences
- Predictable code quality
- CI enforces gates before merge
