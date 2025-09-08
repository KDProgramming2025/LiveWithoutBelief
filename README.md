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
	- Deploy procedure: see `server/README_DEPLOY.md`

## Quality
`./gradlew quality` runs: tests, detekt, spotlessCheck, coverage verify, dependencyGuard.

Coverage thresholds (line): overall 70%, core 80%, data 70%, feature 60%.

## Authentication (LWB-25)
Implemented dual-path authentication:

1. Google Identity Services (silent-first, One Tap / fallback interactive) + Firebase credential sign-in.
2. Username / Password with reCAPTCHA v3 (beta SDK) protection.

Client abstractions:
- `AuthFacade` – public interface consumed by UI.
- `GoogleSignInClientFacade` & `GoogleSignInIntentExecutor` – decouple Google Play Services & Activity Result.
- `SignInExecutor` – wraps Firebase credential sign-in (await logic isolated).
- `TokenRefresher` – isolates token Task -> suspend bridging.
 - `RecaptchaTokenProvider` – modern Google reCAPTCHA enterprise client wrapper.
 - `CachingRecaptchaProvider` – short‑TTL cache (60s) to reduce token fetch latency.
 - `SessionValidator` – chooses validate endpoint (Google vs password JWT) heuristically.

Password flow:
 - Endpoints: `/v1/auth/register`, `/v1/auth/login`, `/v1/auth/pwd/validate`, unified `/v1/auth/revoke`.
 - JWT (`typ: "pwd"`) signed with `PWD_JWT_SECRET`, 1h expiry (client refresh TBD).
 - Revocation store in-memory (future persistent store / Redis TODO).

reCAPTCHA:
 - Client fetches token per action (`LOGIN`), short caches.
 - Server verifies via `https://www.google.com/recaptcha/api/siteverify` with configurable min score (`RECAPTCHA_MIN_SCORE`, default 0.1).
 - Missing / invalid / low-score produce `400 { error: "recaptcha_failed" }`.

Error/UI states:
 - Region blocked Google sign-in -> `AuthUiState.RegionBlocked` banner + password UI.
 - reCAPTCHA failure -> explicit error prompt; user can retry.

Security TODOs:
 - Rate limiting & incremental backoff on password failures.
 - Persist revocations (Redis / DB) & rotate JWT secret automatically.
 - Introduce password strength meter + breach check (HIBP k‑anon) (separate epic).
Secure storage via `EncryptedSharedPreferences` (AES256) stores ID token + basic profile (name/email/avatar).
Sign-out clears storage and invokes placeholder `SessionValidator.revoke` (future backend integration).
Instrumentation test (`GoogleSignInInstrumentedTest`) exercises interactive flow smoke; silent path & refresh covered by unit tests.

### Future Hardening Ideas
- Replace `NoopSessionValidator` with backend endpoint (token introspection + revocation) once server ready.
- Map exceptions to structured domain errors (cancellation, network, developer config) for richer UI states.
- Record analytics events on sign-in success/failure (post observability epic E8).
 - Migrate deprecated Google Sign-In classes fully to Credential Manager when stable coverage is sufficient.

### Server Deployment

Node backend (`server/`): Fastify 5, TypeScript.

Environment variables (systemd `EnvironmentFile=/etc/lwb-server.env`):
```
GOOGLE_CLIENT_ID=...apps.googleusercontent.com
PWD_JWT_SECRET=<32+ hex chars>
RECAPTCHA_SECRET=<server secret key>
RECAPTCHA_MIN_SCORE=0.3   # optional (0.1 default)
PORT=4433
```

Local dev: Put the same keys in `server/.env` (gitignored). The Android app consumes site key via `BuildConfig.RECAPTCHA_SITE_KEY` (set through Gradle property `RECAPTCHA_KEY` or env `RECAPTCHA_KEY`).

Deploy script: `server/deploy.sh` supports `--dry-run`, `--reload`, rollback on failed health.

### Testing reCAPTCHA
Unit tests (`index.test.ts`) mock `fetch` to exercise:
 - Success path (score 0.9)
 - Failure (`success:false`)
 - Low score (< threshold)

Manual QA checklist:
 - Register without token (expect 400).
 - Register with valid token (200, JWT returned).
 - Login with reused cached token (200) within TTL.
 - After revocation, validate returns 401.

### Threat considerations (initial)
| Threat | Current Mitigation | Next Step |
|--------|--------------------|-----------|
| Credential stuffing | reCAPTCHA gating | Add IP+account rate limit |
| Token theft reuse | Revocation endpoint | Shorter expiry + refresh token rotation |
| Replay of reCAPTCHA | Short TTL cache, server verifies score | Bind action names & possibly user fingerprint |
| Brute force password | Min length + reCAPTCHA | Progressive delay/backoff + lockout policy |

### Quick Start (Backend)
```
cd server
npm install
cp .env.example .env  # create & edit values
npm run build && npm start
```

### Quick Start (Android)
```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/*.apk
```

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