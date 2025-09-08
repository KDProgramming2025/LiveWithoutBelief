Current status (2025-09-09):
- Backend running on Node 20 at port 4433 behind nginx (/lwb-api/).
- PostgreSQL 16 provisioned with role lwb_user and db lwb_db; users table exists and owned by lwb_user.
- Server uses PostgresUserStore (DATABASE_URL in /etc/lwb-server.env).
- End-to-end checks via server_commands.sh:
	- Register without reCAPTCHA returns 400 recaptcha_failed (expected).
	- Login testuser/Password123! returns 200 locally and via public nginx.
	- /v1/auth/pwd/validate returns 200.

Next:
- Android: wire AuthViewModel UI to call password registration (with reCAPTCHA) and login, using BuildConfig.AUTH_BASE_URL.
- Optional: add DB migrations framework and persist revocations.