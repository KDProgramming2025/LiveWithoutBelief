# Live Without Belief – Architecture Overview

Issue: LWB-18

## Layering

```
core:model  -> pure data classes
core:common -> shared utilities (Result, helpers)
core:domain -> use case interfaces + abstractions (depends on model/common)
data:network -> Retrofit DTO + API definitions (depends on domain for interfaces if needed)
data:repo    -> Room entities/DAO + repository impls (depends on domain; wires network+db)
feature:*    -> UI + ViewModels consuming domain use cases (no data impl imports)
app          -> Composition root (Hilt modules, navigation, theming)
server       -> Node TS backend (manifest + future content)
```

## Principles
- One-way dependency flow inward toward domain.
- UI talks only to domain (interfaces / use cases), never data impl packages.
- Repositories expose `Flow<Result<T>>` for uniform loading/success/error.
- Hilt provides DI across module boundaries; only app owns concrete bindings of repo impl -> domain interface.

## Data Flow Example (Articles)
1. UI requests articles via `GetArticlesUseCase`.
2. Use case delegates to `ArticleRepository` (domain interface).
3. Implementation (`ArticleRepositoryImpl`) loads from Room (`ArticleDao`) and may sync manifest from network.
4. Emissions wrapped in `Result` and collected by `ReaderViewModel` -> Compose state.

## Quality & Tooling
- `quality` Gradle task: unit tests, detekt, lint, coverage verification, dependency guard.
- Dependency guard blocks feature modules from importing data implementation packages.
- Coverage threshold global (raise per-module later).
- ADRs track decisions (`docs/adr`).

## Testing Strategy
- Domain: pure JVM tests (no Android deps) – fast.
- Data: repository tests with fakes for DAO & API (no instrumentation yet).
- Feature: ViewModel tests with mocked use cases.
- Future: Compose UI snapshot / Paparazzi, integration tests, contract tests against server.

## Pending Enhancements
- Per-module coverage rules (refine Kover filters).
- Error path & retry policy standardization.
- Offline-first caching strategy doc.
- Google Sign-In integration plan.
- Internationalization strategy ADR when multi-language added.

## CI/CD
- GitHub Actions + GitLab mirror both run `./gradlew quality` and Node backend tests.
- Smart commit messages update Jira (issue key + `#time`).

## Branch & PR Flow
- Feature branches: `feature/<ISSUE_KEY>-<slug>`.
- PR Checklist (informal for now): green CI, updated ADR if decision changed, tests added, no dependency guard violations.

---
Generated as part of LWB-18 foundation to aid reviewers.