# Auth Refactor Notes

- New endpoint: POST /v1/auth/google { idToken }
- Requires env: GOOGLE_CLIENT_ID (and optionally GOOGLE_CERTS_BYPASS=1, GOOGLE_ALLOWED_AUDIENCES)
- Production requires DATABASE_URL; service will refuse to start without it.
- Legacy endpoints removed: /v1/auth/validate, /v1/auth/revoke, /v1/auth/register, /v1/auth/login, /v1/auth/pwd/validate

Deploy checklist:
- Ensure /etc/lwb-server.env includes DATABASE_URL=postgres://...
- scp server_commands.sh lwb-server:/tmp/server_commands.sh
- ssh lwb-server "bash /tmp/server_commands.sh"
