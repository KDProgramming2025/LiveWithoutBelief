Session recap (LWB Admin UI/API + Client ALTCHA)

- Deployed Admin Web/API previously via server/deploy.sh; API bound to 127.0.0.1:5050 and nginx proxies /LWB/Admin/api.
- Fixed edit endpoint to persist title when sent via multipart FormData (string or {value}). Deployed and restarted service.
- Fixed server-side permissions for Admin API data/public dirs (/opt/lwb-admin-api/data, /var/www/LWB/Articles) and ensured www-data ownership.
- Synced admin credentials to /etc/lwb-server.env and restarted lwb-admin-api.
- Verified login/session both locally and through nginx proxy: authenticated:true; Admin UI should now log in with .env credentials.
- Cleared server_commands.sh back to a comment-only template (ad-hoc policy respected).

Relevant paths/state
- Systemd: lwb-admin-api.service (Node 20); healthy and listening on 127.0.0.1:5050.
- Nginx: Admin UI at https://aparat.feezor.net/LWB/Admin; API proxied at /LWB/Admin/api; Articles served at https://aparat.feezor.net/LWB/Articles/<slug> via root /var/www.
- Storage: secure /opt/lwb-admin-api/data; public /var/www/LWB/Articles.

Client app updates
- Fixed Android ALTCHA solver: use prefix match for challenge (per altcha-lib) instead of full-hash equality.
- Added AltchaSolver unit tests. Registration should no longer fail with altcha_failed due to solver mismatch.

Recent ops (Users panel → Postgres direct)
- Found DATABASE_URL missing in /etc/lwb-server.env; restored from backup and restarted lwb-admin-api.
- Bundled pg into admin/api build (tsup noExternal includes 'pg') to avoid missing runtime deps.
- Hard-reset server repo to remote branch, rebuilt, and redeployed Admin Web/API via server/deploy.sh.
- Verified via local and nginx-proxied probes: /v1/admin/users/summary now returns total=7; /v1/admin/users/search returns newest users.

Production fix (server_error on Users endpoints)
- Root cause: Admin API queried columns last_login/deleted_at that didn’t exist yet on the production DB.
- Fix: Added safe startup migration in admin/api buildUserStore: ensureSchema() creates users table if missing and adds last_login, deleted_at via ALTER TABLE IF NOT EXISTS; all user queries call ensureSchema().
- Deployed and verified on server: node+pg check OK, Users summary/search return data (total=7), delete endpoint works.

New changes (Users: last-login + remove)
- Server API: Updates last_login on successful password login; blocks deleted users on password login. Google validate now loads unified env and blocks if a mapped local user exists and is deleted; updates last_login for known users.
- Admin API: Switched to HARD DELETE. Users count/search no longer filter by deleted_at. Startup purges legacy soft-deleted rows. DELETE /v1/admin/users/:id returns 200 when a row is deleted; 404 when id never existed.
- Admin Web: Renders lastLogin; Remove button shows confirmation modal; after delete, refresh totals/search and tolerate 404 on repeat.

Operational guardrails (server_commands.sh)
- Purpose: Ephemeral scratchpad for commands to run on the VPS. Never persist commands.
- Usage flow: write commands → scp to lwb-server:/tmp/server_commands.sh → ssh to run → immediately empty the file and commit the empty version.
- Note: On the VPS, the shebang line is not recognized as /usr/bin/env; script still runs because we invoke via `bash /tmp/server_commands.sh`. Keep first line commented or ensure bash invocation.

Today’s deployment & probes
- Deployed updated Admin API/Web via server/deploy.sh; service restarted and healthy behind nginx.
- Verified hard delete behavior in logs: DELETE returned 200 for existing ids; 404 for unknown ids. Users summary/search updated accordingly.
- Main Server API build succeeded; systemd unit not present on VPS (warning): lwb-server.service not found; restart skipped.

Next steps (small)
- If you want Google validate to block identities after hard delete, consider a separate banned list (since row is gone).
- Keep server_commands.sh ephemeral and empty after use.

Do not repeat
- Re-creating/repairing dirs or credentials unless they change.
- Never commit server_commands.sh. It's intentionally gitignored; write ad-hoc commands locally, scp to /tmp/server_commands.sh on the server, ssh and run, then clear the file.

Today: auth persistence + server deploy helper
- Android: AutoTokenRefresher now validates any stored token once on app start via SessionValidator. This preserves login across restarts and triggers server-side last_login updates when the app starts.
- DI: Wired SessionValidator into AutoTokenRefresher provider.
- Server: Added scripts/deploy-main-server.sh for main API; populated server_commands.sh with one-shot commands to build, install a lwb-server systemd unit (port 4433), and add nginx /lwb-api/ proxy include if missing. Remember to empty server_commands.sh after executing.
