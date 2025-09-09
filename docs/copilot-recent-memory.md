Current status (2025-09-09):
- Backend running on Node 20 at port 4433 behind nginx (/lwb-api/).
- PostgreSQL 16 provisioned with role lwb_user and db lwb_db; users table exists and owned by lwb_user.
- Server uses PostgresUserStore (DATABASE_URL in /etc/lwb-server.env).
- End-to-end checks via server_commands.sh:
	- Register without reCAPTCHA returns 400 recaptcha_failed (expected).
	- Login testuser/Password123! returns 200 locally and via public nginx.
	- /v1/auth/pwd/validate returns 200.

09-09 updates:
- Enabled dev reCAPTCHA bypass on server (RECAPTCHA_DEV_ALLOW=1) via systemd EnvironmentFile and drop-in; verified journal shows recaptcha_dev_bypass.
- With bypass: unique username registration returns 200; existing username returns 409 (expected). Login still 200.
- Fixed server deploy.sh (log before init) and added manual deploy fallback to server_commands.sh. Origin remote configured on server.
- Android BuildConfig AUTH_BASE_URL points to https://aparat.feezor.net/lwb-api; app installed successfully.

Next:
- Android: fixed NetworkOnMainThreadException by wrapping OkHttp execute in Dispatchers.IO; reinstalled app to device.
- Verify on-device login and registration paths now proceed without main-thread crash; observe logs.
	- Reinstalled debug APK successfully to device M2010J19SG (Android 12) via Gradle :app:installDebug.
- Optional: add DB migrations framework and persist revocations.
- After on-device confirm, disable dev bypass for prod; ensure reCAPTCHA site key/secret and allowed origins are correct.