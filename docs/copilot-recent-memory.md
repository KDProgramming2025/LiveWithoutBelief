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

New changes (Users: last-login + remove)
- Server API: Added last_login and deleted_at columns (auto-migration in userStore). Update last_login on successful password login, block deleted users.
- Admin API: Users count/search now filter deleted users and include lastLogin. Implemented DELETE /v1/admin/users/:id as soft-delete (sets deleted_at).
- Admin Web: Already renders lastLogin and wired Remove button; should now work end-to-end.

Operational guardrails (server_commands.sh)
- Purpose: Ephemeral scratchpad for commands to run on the VPS. Never persist commands.
- Usage flow: write commands → scp to lwb-server:/tmp/server_commands.sh → ssh to run → immediately empty the file and commit the empty version.
- Enforcements: Added CI workflow .github/workflows/guard-server-commands.yml and local pre-commit hook scripts/githooks/pre-commit to fail when non-comment lines exist.

Next steps (small)
- In Admin UI, refresh Users tab; confirm total and search work (pagination optional follow-up).
- Keep server_commands.sh ephemeral and empty after use.

Do not repeat
- Re-creating/repairing dirs or credentials unless they change.
- Never commit server_commands.sh. It's intentionally gitignored; write ad-hoc commands locally, scp to /tmp/server_commands.sh on the server, ssh and run, then clear the file.
