# Live Without Belief

A production-minded, open-source Android reader experience backed by a lightweight Node/TypeScript API and a zero-build admin web console. Articles originate from Word (.docx) sources that are converted server-side to HTML/CSS/JS and rendered in-app via a WebView, enabling rich styling, media, and paragraph-level discussions.

## Highlights
- Android app built with Jetpack Compose, Hilt, MVVM, and SOLID-aligned module boundaries (feature-first). Supports light/dark themes, pagination, appearance controls, and per-paragraph discussions that stay private to each user.
- Content pipeline: Word documents -> server conversion -> HTML payloads -> in-app WebView with reader session state (appearance, progress, page index) separated from acquisition logic.
- Admin web console (no bundler) for uploads, user management, and menu curation with persisted theme/sidebar preferences and progressive uploads.
- Node/TypeScript API (Express, ESM) with clean layering (domain, repositories, services, routes) and systemd-friendly deployment on Linux.
- CI on GitHub Actions: matrixed Java/SDK builds, unit + UI tests, Detekt/lint, Kover coverage, SARIF upload, Node lint/tests.
- Quality gates enforced locally: Detekt, Kover, lint, spotless license header, benchmark module for macrobenchmarks, and design-system library for consistent UI.

## Repository Map
- Android app and libraries
  - app/ — Android application shell (navigation, DI, BuildConfig wiring, Compose setup)
  - core/common/, core/domain/, core/model/ — Shared contracts and utilities
  - data/network/, data/repo/ — Networking and persistence abstractions/implementations
  - feature/annotations/, feature/articles/, feature/home/, feature/reader/, feature/search/, feature/settings/ — Feature slices
  - ui/design-system/ — Reusable Compose components and tokens
  - benchmark/ — Macrobenchmark tests and Baseline Profile TODOs
- Web admin console
  - admin/web/ — Static ES module app (see below and admin architecture doc)
- Backend API
  - server/ — Node + TypeScript API (Express) with domain/repository/service layering
- Tooling and docs
  - .github/workflows/android.yml — CI pipeline
  - scripts/ — Detekt helpers and assorted utilities
  - docs/ — Architecture notes (reader and admin modules)

## Android App Overview
- Tech stack: Kotlin, Jetpack Compose, Hilt, KSP, Coroutines/Flows, Kotlinx Serialization, modular Gradle build, Compose Material 3.
- Architecture: Clean-inspired layering with `domain` use cases feeding feature ViewModels; UI is Compose-first; DI via Hilt; navigation and BuildConfig values surfaced through `app`.
- Reader-specific architecture: separation of article acquisition vs. reading session state (pagination, appearance, progress) described in [docs/reader-architecture.md](docs/reader-architecture.md).
- Feature highlights: article list and detail, WebView reader for converted HTML, per-user private discussions on selected paragraphs or media, theming (light/dark), appearance controls (font scale, line height, background), and progress persistence.
- Modules for discoverability: design-system for visual consistency; annotations for shared UI annotations; settings/search/home/articles/reader feature modules.

### Android Build & Run
Prerequisites: Android Studio Ladybug or later, JDK 17/21, Android SDK 36, Google Play Services JSON (optional), and optional `.env` at repo root for server host/client IDs.

Common Gradle tasks:
```bash
# Clean, assemble, and run unit tests
./gradlew --stacktrace --parallel assembleDebug testDebugUnitTest

# Full quality gate (Detekt, lint, Kover) — matches CI quality job
./gradlew --stacktrace --no-configuration-cache quality

# Install debug build on a connected device/emulator
./gradlew app:installDebug -x lint -x test

# Managed device UI tests (same as CI)
./gradlew :app:pixel9xlApi36DebugAndroidTest --no-configuration-cache

# Macrobenchmark (example: startup)
./gradlew :benchmark:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=info.lwb.benchmark.StartupBenchmark
```
Configuration inputs (auto-resolved where possible):
- BuildConfig derives API endpoints from env/Gradle props or `.env` keys (`APP_SERVER_HOST`, `API_BASE_URL`, `AUTH_BASE_URL`, `UPLOADS_BASE_URL`).
- Google client IDs resolved from env/Gradle props, `google-services.json`, or `.env` fallbacks.
- Optional signing supplied via `SIGNING_KEYSTORE_*` env/props.

## Admin Web Console
- Frameworkless ES modules + modular CSS; zero build step.
- Responsibilities and file layout detailed in [docs/admin-module-architecture.md](docs/admin-module-architecture.md).
- Capabilities: login/logout (token persistence), theme toggle with persisted preference, sidebar state persistence, Lucide icon lazy loading, view router, menu CRUD, article upload with progress/ETA, user search/delete.
- Run locally: open admin/web/index.html in a browser (file:// works for most flows; configure API base paths as needed).

## Backend API Server
- Minimal Express + TypeScript (ESM) server documented in [server/README.md](server/README.md).
- API surface: health check, article manifest, article by id, auth (email register, username/password register/login with ALTCHA verification).
- Config precedence: `LWB_ENV_FILE` → repo `.env` (production) → `server/.env.local` (dev). Production env typically lives at `/etc/lwb-server.env`.
- Project layout: `src/domain`, `src/repositories`, `src/services`, `src/server`.
- Local dev: `npm ci`, `npm run dev` (uses nodemon/ts-node). Type-check/build: `npm run build`. Tests: `npm test` (Vitest + coverage).
- Production deploy (Linux, systemd): clone to `/var/www/LWB/server`, ensure Node at `/opt/lwb-node/current/bin/node`, copy env to `/etc/lwb-server.env`, `npm ci && npm run build`, enable `lwb-server.service` (unit in `server/deploy/lwb-server.service`).

## CI/CD (GitHub Actions)
Pipeline lives in [.github/workflows/android.yml](.github/workflows/android.yml):
- Android job (matrix java 17/21, SDK 36, build-tools 35): checkout → JDK setup → SDK cache/install → unit tests (`testDebugUnitTest`) → triage report upload → assembleDebug → quality (`quality`: Detekt, lint, coverage) on Java 17 → managed device UI tests (Pixel 9 XL API 36) → publish Detekt SARIF, lint, coverage, test results, APK artifacts.
- Node job (matrix node 20/22): npm ci, ESLint, Vitest with coverage, coverage artifact upload.
- Dependency review job for pull requests.

## Quality & Testing Expectations
- Static analysis: Detekt config in detekt.yml; use helper scripts `scripts/list-detekt-issues.js` and `scripts/list-detekt-issues-for-file.js` to zero violations.
- Formatting/licensing: Spotless license header enforced via spotless.license.kt presets.
- Tests: JUnit on JVM, Compose UI tests on managed devices, Vitest on server, macrobenchmarks in benchmark/.
- Coverage: Kover reports uploaded in CI; aim to keep parity locally via `./gradlew koverXmlReport` or `quality` aggregate.
- Accessibility/UX: Admin panel maintains ARIA attributes, theme persistence, and respects reduced-motion (per architecture doc); app uses Compose Material semantics.

## Content & Data Flow
1) Authoring: Create articles in Word with styling/media; server converts .docx to HTML/CSS/JS.
2) Distribution: Android fetches article manifest and HTML payloads via API base URLs set in BuildConfig.
3) Reading: WebView renders HTML; `ReaderSessionViewModel` paginates blocks, tracks appearance settings, and persists progress while `ArticlesViewModel` handles acquisition ([docs/reader-architecture.md](docs/reader-architecture.md)).
4) Discussion: Users sign in/register; per-paragraph or media discussions are private to the initiating user (no global threads).

## Admin & Ops Notes
- Server path convention: `/var/www/LWB/` with env file at `/etc/lwb-server.env`.
- Node install on server is isolated at `/opt/lwb-node/current` — use that path in services and scripts.
- Deployment helper unit files and nginx snippets are under server/deploy/.

## Contributing Workflow (recommended)
1) Plan work in Jira; link issues to GitHub branches/PRs.
2) Branch naming: feature/<slug> or fix/<slug>.
3) Develop with TDD where possible; keep feature modules isolated and lean (SRP).
4) Run local quality gates: `./gradlew quality`, UI tests if UI touched, `npm test` for server changes, Detekt scripts for quick feedback.
5) Open PR; CI must be green. Address Detekt/lint issues before merge.
6) Release: tag as `vX.Y.Z`; Android versionCode/Name derive from `GIT_TAG` automatically.

## Additional References
- Reader feature decision record: [docs/reader-architecture.md](docs/reader-architecture.md)
- Admin web module architecture: [docs/admin-module-architecture.md](docs/admin-module-architecture.md)
- Benchmark usage: [benchmark/README.md](benchmark/README.md)
- Server API details: [server/README.md](server/README.md)

---
Built to showcase modern Android + web + Node craftsmanship, ready for hiring panels and real users alike.
