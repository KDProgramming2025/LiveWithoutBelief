Date: 2025-09-12
Branch: feature/LWB-92-admin-ui (active)

Current State Summary:
- Admin API (Fastify/TS) scaffolded with endpoints: GET /v1/admin/ingestion/queue and GET /v1/admin/support/users.
- Admin Web (Vite/React/TS) scaffolded; added App.tsx showing Queue + User Support; build passes.
- Fixed types and tooling: added @fastify/cors, vitest configs, minimal tests for API and Web; both test suites passing locally.

Immediate Next Steps:
1. Flesh out LWB-93: table UI with status badges, auto-refresh, and manual refresh.
2. LWB-94: user support view with search/filter and basic detail drawer.
3. LWB-95: wire API base URL via env; add proxy to real backend when available; basic error states.

Notes:
- No PR to be opened yet (owner will test first).
- Keep Android app unaffected; continue to run full Gradle checks before committing large changes.
