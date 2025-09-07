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

## Authentication (LWB-25)
Implemented Google Identity Services sign-in with silent-first attempt and interactive fallback. Abstractions:
- `AuthFacade` – public interface consumed by UI.
- `GoogleSignInClientFacade` & `GoogleSignInIntentExecutor` – decouple Google Play Services & Activity Result.
- `SignInExecutor` – wraps Firebase credential sign-in (await logic isolated).
- `TokenRefresher` – isolates token Task -> suspend bridging.
Secure storage via `EncryptedSharedPreferences` (AES256) stores ID token + basic profile (name/email/avatar).
Sign-out clears storage and invokes placeholder `SessionValidator.revoke` (future backend integration).
Instrumentation test (`GoogleSignInInstrumentedTest`) exercises interactive flow smoke; silent path & refresh covered by unit tests.

### Future Hardening Ideas
- Replace `NoopSessionValidator` with backend endpoint (token introspection + revocation) once server ready.
- Map exceptions to structured domain errors (cancellation, network, developer config) for richer UI states.
- Record analytics events on sign-in success/failure (post observability epic E8).

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