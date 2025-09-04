# ADR 0004: Static Analysis & Coverage Gating

Date: 2025-09-04

## Status
Accepted

## Context
We need consistent, automated code quality enforcement across a multi-module Android + Node (server) repository to ensure:
* Early detection of style, complexity, and architectural drifts.
* Enforce test coverage baselines to avoid regression in critical layers.
* Provide a professional workflow matching mid-size company standards.

Existing tooling: Detekt (no baseline yet), Kover global rule (70%), custom dependency guard, Vitest for server.

## Decision
Introduce the following:
1. Spotless + ktlint for formatting & style; enforced via `spotlessCheck` in `quality` pipeline and `spotlessApply` local.
2. Refined Kover verification rules (overall + layered thresholds):
   * Overall >= 70% (temporary baseline).
   * Core (info.lwb.core.*) >= 80%.
   * Data (info.lwb.data.*) >= 70%.
   * Feature (info.lwb.feature.*) >= 60% (UI heavy; will raise later as UI tests appear).
3. License header enforcement using root `LICENSE` for Kotlin sources.
4. `quality` task now depends on: all unit tests, lint, detekt, koverXmlReport, dependencyGuard, spotlessCheck.
5. Future (not yet implemented):
   * Android Lint baseline once UI modules expand.
   * Detekt baseline only if unavoidable noise emerges (prefer fixing issues instead of suppressing).
   * Optional SARIF export (can be enabled when GitHub/GitLab code scanning configured).
6. Suppression policy: prefer local `@Suppress("DetektRule")` with justification comment; avoid global disable.
7. Coverage exclusions: generated code (Hilt, Room, buildConfig) implicitly excluded by default patterns—no manual broad exclusions added yet.

## Alternatives Considered
* Using only ktlint CLI without Spotless – rejected (Spotless provides multi-format + license header + simpler Gradle integration).
* Global single coverage threshold – rejected (layers differ in testability; stratified thresholds encourage proper unit testing where most valuable).
* Immediate high thresholds (>=85% everywhere) – rejected (premature; raises friction before UI & infra tests mature).

## Consequences
* Developers must run `./gradlew formatAll` or rely on `spotlessApply` before committing to avoid CI failures.
* PRs failing coverage must add/adjust tests instead of reducing thresholds.
* Adds minor build time overhead (Spotless + extra Kover filters), acceptable for quality gains.

## Follow-Up Tasks
* Add pre-commit Git hook invoking `spotlessCheck` + `detekt` (Story TBD).
* Introduce UI screenshot / Paparazzi tests to lift feature module coverage.
* Consider enabling compiler `-Werror` after initial warning audit.
* Evaluate dependency analysis plugin for unused/undeclared detection (could supplement custom guard).

## References
* Detekt config: `detekt.yml`
* Root Gradle changes: `build.gradle.kts`
