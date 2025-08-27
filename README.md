# Live Without Belief

A modern Android + Spring Boot open source app that delivers evidence-based critical analysis articles of religious texts with:
- Remote article sync + versioning
- Hybrid pagination reader (Compose)
- Private annotations & attachments (planned)
- Full‑text local search (planned)
- Lazy Google sign‑in (planned)
- Offline per‑article download (planned)

## Modules
| Module | Type | Purpose |
|--------|------|---------|
| app | Android application | DI wiring, navigation host |
| core:model | JVM | Pure data models (serialization) |
| core:common | JVM | Shared utilities (Result, etc.) |
| data:network | Android lib | Retrofit APIs & network DI |
| data:repo | Android lib | Room DB + repositories |
| feature:* | Android lib | Feature UIs (reader, search, bookmarks, annotations) |
| server | Spring Boot | Backend APIs, manifest + article endpoints |

## Tech Stack
- Kotlin (Android + JVM)
- Jetpack Compose, Material3, Hilt, Room, Retrofit + kotlinx.serialization
- Coroutines & Flow
- Spring Boot 3 (web, data-jpa, security minimal, actuator, Flyway, H2 for dev)
- Static analysis: ktlint, detekt
- Testing: JUnit, Coroutines test, Paparazzi (Compose snapshots)

## Build
```bash
# Windows
./gradlew.bat build
# *nix
./gradlew build
```
Artifacts: `app/build/outputs/apk/` and server JAR at `server/build/libs/`.

## Run Server (Dev)
```bash
./gradlew :server:bootRun
```
Endpoint example:
```
GET http://localhost:8080/v1/articles/manifest
```

## Run Paparazzi Snapshot Tests
```bash
./gradlew :feature:reader:testDebugUnitTest --tests *Snapshot*
```

## Code Style & Lint
- Ktlint & detekt run in CI and local build. To format:
```bash
./gradlew ktlintFormat
```

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md)

## License
Apache-2.0 (see LICENSE)

## Roadmap (MVP Remaining)
- Article manifest sync UI list
- Annotation model & private threads
- Offline download worker
- Search FTS integration
- Google sign‑in & minimal user record
- CI artifact publishing

## Security / Privacy
- No email stored; only hashed Google `sub` planned.
- All annotations private per user (future endpoint design).

## CI
GitHub Actions: android-ci (build + lint + tests). Jenkins & GitLab mirror planned for parity.

## Badges (to add after repo push)
```
![CI](https://github.com/<org>/live-without-belief/actions/workflows/android-ci.yml/badge.svg)
```

