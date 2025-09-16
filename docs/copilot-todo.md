# Copilot TODO (Server API Backend)

Date: 2025-09-16

## Completed
- Pushed branch `feature/LWB-92-admin-ui` to remote `github`.
- Analyzed Android app modules and identified required API endpoints.
- Verified/implemented Node+TS server under `server/` with Express:
	- GET `/v1/articles/manifest`, GET `/v1/articles/{id}`
	- POST `/v1/auth/google`, POST `/v1/auth/register`
	- POST `/v1/auth/pwd/register`, POST `/v1/auth/pwd/login`
	- GET `/v1/altcha/challenge`
- Added unit tests for `AltchaService` and `AuthService`.
- Added `server/README.md` and `.env.example`.
- Build + tests pass locally (no server run).

## In-progress / Planned (Server hardening)
- Replace in-memory repositories with PostgreSQL-backed implementations.
- Use Argon2id or bcrypt for password hashing.
- Replace custom ALTCHA with official altcha-org library.
- Optionally implement real Google ID token verification.
- Add DB migrations and CI checks for the server package.

## Notes
- Do not run the server locally; deploy via systemd on the VPS.

