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

Next steps (small)
- UI: Login and hit Articles tab; if list is empty, click a new Reindex action (or call POST /v1/admin/articles/reindex) to build metadata from existing public articles.
- Confirm GET /LWB/Admin/api/v1/admin/articles returns items immediately after upload; we added atomic-write + cache and a filesystem bootstrap.
- Future: Wire User Management endpoints to existing Postgres backend.
 - Verify password registration end-to-end on device; if still failing, check server ALTCHA_HMAC_KEY and ensure AUTH_BASE_URL points to the correct host.

Do not repeat
- Re-creating/repairing dirs or credentials unless they change.
- Never commit server_commands.sh. It's intentionally gitignored; write ad-hoc commands locally, scp to /tmp/server_commands.sh on the server, ssh and run, then clear the file.
