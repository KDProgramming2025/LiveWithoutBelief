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
- Replace in-memory repositories with PostgreSQL-backed implementations. (users: DONE)
- Use Argon2id or bcrypt for password hashing. (argon2id: DONE)
- Replace custom ALTCHA with official altcha-org library. (DONE)
- Optionally implement real Google ID token verification. (Deferred)
- Add DB migrations and CI checks for the server package. (migrations: DONE)

## Deployment Progress (VPS)
- Repo cloned to /var/www/LWB with remote `github`. (DONE)
- Node deps installed; TypeScript build succeeds on VPS. (DONE)
- PostgreSQL role/db created; ownership/privileges granted. (DONE)
- Applied 001_init.sql migration; fixed FK type mismatch; tables recreated. (DONE)
- Wrote /etc/lwb-server.env with runtime config. (DONE)
- Installed systemd unit; daemon-reload; enabled; started. (DONE)
- Fixed crash by switching from browser `altcha` to server-side `altcha-lib`. (DONE)
- Service status: active (running). (DONE)

## Notes
- Do not run the server locally; deploy via systemd on the VPS.
 - New env: `ALTCHA_MAXNUMBER` (optional). Lower values make challenges easier/faster. Set in `/etc/lwb-server.env` and restart service.

## Next
- [ ] App UX: show a small "Solving challenge…" indicator during registration and add a 10–15s timeout + retry.
- [ ] Server: tune ALTCHA difficulty via `ALTCHA_MAXNUMBER` for production vs. debug.

