# Live Without Belief (LWB)

An open-source Android app exploring scientific analysis of religious texts and offering secular guidance.

## Features
- Reader UI with media, in-article search, pagination, themes
- Google Sign-In (one-tap)
- Bookmarks and search
- Private annotations and discussion threads (text/image)

## Tech stack
- Kotlin, Jetpack Compose, Hilt, Room, Coroutines/Flow
- Multi-module Clean Architecture
- Spotless, Detekt, Lint, GitHub Actions CI

## Getting started
1. Requirements: Android Studio (Koala+), JDK 17, Android SDK 26+
2. Build on Windows PowerShell:
   ```powershell
   ./gradlew.bat build
   ```
3. Run the app from Android Studio (select app configuration).

## Project structure (high level)
- `app/`: app shell, auth, navigation
- `core/`: common, domain, model
- `data/`: repo/db/network
- `feature/`: reader, bookmarks, annotations, search
- `ui/`: design system
- `server/`: backend (Node/TS) skeleton

## Contributing
See CONTRIBUTING.md.

## Documentation
- ADRs: `docs/adr/`
- API (OpenAPI): `docs/api/openapi.yaml`

## Releases and versions (in plain English)

- When we merge to `main`, a bot (release-please) prepares a "Release PR" with a changelog and a version like `v0.2.0`.
- When we merge that Release PR, GitHub creates a tag and a Release. Our Android build reads that tag to set the app version automatically.
- You don’t need to type versions into Gradle. The tag drives `versionName` and `versionCode`.

### Optional: Play Console upload (internal testing)

We can later add a CI job to upload a build to the Play Console’s internal testing track. That needs Play API credentials, and the app module will use the "Play Publisher" Gradle plugin to push the AAB. You’ll provide credentials as CI secrets. This is optional and can be added when you’re ready to test on Play.

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

CI performance notes
- Gradle build cache and configuration-cache are enabled; Actions use setup-gradle caching.
- Static analysis (quality) runs on a single JDK in the matrix to reduce duplication.

TDD/Test Strategy
- See `docs/test-strategy.md` for layers, TDD workflow, fixtures/builders, and CI mapping.

## Authentication (LWB-25)
Implemented dual-path authentication:

1. Google Identity Services (silent-first, One Tap / fallback interactive) + Firebase credential sign-in.
2. Username / Password with ALTCHA challenge (self-hosted) on registration.

Client abstractions:
- `AuthFacade` – public interface consumed by UI.
- `GoogleSignInClientFacade` & `GoogleSignInIntentExecutor` – decouple Google Play Services & Activity Result.
- `SignInExecutor` – wraps Firebase credential sign-in (await logic isolated).
- `TokenRefresher` – isolates token Task -> suspend bridging.
 - `AltchaTokenProvider` – WebView-based solver that retrieves an ALTCHA solution token.
 - `SessionValidator` – chooses validate endpoint (Google vs password JWT) heuristically.

Password flow:
 - Endpoints: `/v1/auth/register`, `/v1/auth/login`, `/v1/auth/pwd/validate`, unified `/v1/auth/revoke`.
 - JWT (`typ: "pwd"`) signed with `PWD_JWT_SECRET`, 1h expiry (client refresh TBD).
 - Revocation store in-memory (future persistent store / Redis TODO).

ALTCHA:
 - Client presents a first-party challenge page (asset) to solve and obtains a signed token.
 - Server exposes `GET /v1/altcha/challenge` and verifies the solution (HMAC, expiry).
 - Missing/invalid solutions produce `400 { error: "altcha_failed" }`.

Error/UI states:
 - Region blocked Google sign-in -> `AuthUiState.RegionBlocked` banner + password UI.
 - ALTCHA failure -> explicit error prompt; user can retry.

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
ALTCHA_HMAC_KEY=<32+ hex chars>
ALTCHA_EXPIRE_SECONDS=120
PORT=4433
```

Local dev: Put the same keys in `server/.env` (gitignored). The Android app uses a bundled asset `altcha-solver.html` and does not require Google keys for CAPTCHA.

Deploy script: `server/deploy.sh` supports `--dry-run`, `--reload`, rollback on failed health.

### Testing ALTCHA
Unit tests (server) validate:
 - Challenge issuance (HMAC and expiry window)
 - Verification success path and common failures (missing/invalid/expired)

Manual QA checklist:
 - Register without token (expect 400).
 - Register with valid token (200, JWT returned).
 - Login with reused cached token (200) within TTL.
 - After revocation, validate returns 401.

### Threat considerations (initial)
| Threat | Current Mitigation | Next Step |
|--------|--------------------|-----------|
| Credential stuffing | ALTCHA gating | Add IP+account rate limit |
| Token theft reuse | Revocation endpoint | Shorter expiry + refresh token rotation |
| Replay of ALTCHA | Signed challenge with expiry | Bind action names & possibly user fingerprint |
| Brute force password | Min length + ALTCHA on register | Progressive delay/backoff + lockout policy |

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