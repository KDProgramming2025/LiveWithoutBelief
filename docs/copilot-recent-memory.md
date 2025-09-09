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

09-09 ALTCHA migration and PR:
- Removed Google reCAPTCHA entirely; added ALTCHA challenge endpoint on server and server-side verification; configured env ALTCHA_HMAC_KEY.
- Android app now fetches and solves ALTCHA for registration; login remains without CAPTCHA; all network calls use Dispatchers.IO.
- Unit tests updated for new constructor deps and re-run: PASS locally (testDebugUnitTest green).
- Branch feature/LWB-25-auth pushed and PR opened: https://github.com/KDProgramming2025/LiveWithoutBelief/pull/6

Next steps:
- Merge PR after review; then create branch for next story.
- Consider rotating ALTCHA_HMAC_KEY and documenting envs in README.

09-09 CI lint fixes:
- Addressed lint SuspiciousIndentation in app by:
	- Bracing single-line debug log in `AuthModule.kt` and catch block.
	- Normalizing indentation inside `viewModelScope.launch {}` blocks in `AuthViewModel.kt`.
- Pushed commit 9bb68ea to feature/LWB-25-auth; local `:app:lintDebug` now passes.
- CI should re-run; monitor PR https://github.com/KDProgramming2025/LiveWithoutBelief/pull/6.