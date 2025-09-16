# Live Without Belief — App API Server

A minimal Node + TypeScript (ESM) server implementing the backend API used by the Android app. No frameworks beyond Express. Clean, SOLID-ish layering with domain, repositories, services, and routes.

## API surface (v1)

- GET /health → { ok: true }
- GET /v1/articles/manifest → List<ManifestItemDto>
- GET /v1/articles/{id} → ArticleDto
- POST /v1/auth/register { email } → 201 with { user } on first create; 200 on subsequent
- POST /v1/auth/pwd/register { username,password,altcha } → 200 { user } | 409 if exists | 400 if ALTCHA invalid (official altcha verification)
- POST /v1/auth/pwd/login { username,password } → 200 { user } | 401 invalid
	(No custom challenge endpoint; use official altcha widget/client in the app)

Data contracts mirror the Android client Kotlin DTOs in `data/network` and `app/auth`.
Google ID token route was removed; registration uses email directly from the client.

## Configuration

Environment is loaded from one of:
1. `LWB_ENV_FILE` absolute path (recommended for systemd: `/etc/lwb-server.env`)
2. Project `.env` in repo root (when `NODE_ENV=production` and 1 not set)
3. `server/.env.local` for development

Variables:
- `PORT` (default 4433)
- PostgreSQL: `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`
	(Pool is created from env; ensure these are set in /etc/lwb-server.env)

See `.env.example` for a template.

## Project layout

- `src/domain` — DTOs
- `src/repositories` — data access (in-memory by default)
- `src/services` — business logic
- `src/server` — HTTP app, routes, config

## Install, build, test (local only)

We don’t run the server locally in production. You can still lint/test/build locally to ensure CI passes:

- Install deps: npm ci
- Type-check: npm run build
- Tests: npm test

## Deploy on Linux VPS via systemd

1) Copy env file (edit values as needed): `/etc/lwb-server.env`

2) Ensure Node is installed at `/opt/lwb-node/current` per project convention and `node` is available to systemd unit via absolute path.

3) Place unit file at `/etc/systemd/system/lwb-server.service`:

```
[Unit]
Description=LWB App API Server
After=network.target

[Service]
EnvironmentFile=/etc/lwb-server.env
WorkingDirectory=/var/www/LWB/server
ExecStart=/opt/lwb-node/current/bin/node dist/index.js
Restart=always
RestartSec=3
User=www-data
Group=www-data

[Install]
WantedBy=multi-user.target
```

4) Build the server on the VPS (from `/var/www/LWB/server`):
- npm ci
- npm run build

Optional: run migrations (single-command policy applies; run each file separately):
- psql "$PGDATABASE" -f db/migrations/001_init.sql

5) Start and enable:
- sudo systemctl daemon-reload
- sudo systemctl enable --now lwb-server
- sudo systemctl status lwb-server

Logs: `journalctl -u lwb-server -f`

## Notes

- Articles are served from an in-memory repository for now. Replace `InMemoryArticleRepository` with a proper implementation (filesystem/DB) later.
- Passwords are hashed via SHA-256 here for simplicity; replace with a proper password hashing strategy (Argon2/bcrypt) when persisting users.

---

SPDX-License-Identifier: Apache-2.0
# Live Without Belief - App API Server

A tiny Node + TypeScript API that serves the Android app.

Endpoints implemented (v1):
- GET /v1/articles/manifest → List of articles (id, title, slug, version, updatedAt, wordCount)
- GET /v1/articles/{id} → Article by id (sections/media/html)
- GET /v1/altcha/challenge → Issue an ALTCHA challenge for password registration
- POST /v1/auth/register → Upsert user by email (Google flow). 200 existing, 201 created
- POST /v1/auth/google → Validate Google ID token (demo: accepts any non-empty token)
- POST /v1/auth/pwd/register → Username/password registration; requires ALTCHA payload base64
- POST /v1/auth/pwd/login → Username/password login

Run locally:
1) Create .env.local (or .env for prod):
```
PORT=4433
ALTCHA_SECRET=dev-secret
```
2) Install and run:
- npm install
- npm run dev

Android app points to http://10.0.2.2:4433/ by default.

Note: This demo uses in-memory storage for users and articles. Replace repositories with persistent stores as needed.