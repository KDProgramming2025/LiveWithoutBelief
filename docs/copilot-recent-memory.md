Session recap (LWB Admin UI/API)

- Deployed Admin Web/API previously via server/deploy.sh; API bound to 127.0.0.1:5050 and nginx proxies /LWB/Admin/api.
- Fixed server-side permissions for Admin API data/public dirs (/opt/lwb-admin-api/data, /var/www/LWB/Articles) and ensured www-data ownership.
- Synced admin credentials to /etc/lwb-server.env and restarted lwb-admin-api.
- Verified login/session both locally and through nginx proxy: authenticated:true; Admin UI should now log in with .env credentials.
- Cleared server_commands.sh back to a comment-only template (ad-hoc policy respected).

Relevant paths/state
- Systemd: lwb-admin-api.service (Node 20); healthy and listening on 127.0.0.1:5050.
- Nginx: Admin UI at https://aparat.feezor.net/LWB/Admin; API proxied at /LWB/Admin/api.
- Storage: secure /opt/lwb-admin-api/data; public /var/www/LWB/Articles.

Next steps (small)
- Optional: UI smoke-test login in browser; try DOCX upload to confirm converted content lands under /var/www/LWB/Articles and meta updates.
- Future: Wire User Management endpoints to existing Postgres backend.

Do not repeat
- Re-creating/repairing dirs or credentials unless they change.
