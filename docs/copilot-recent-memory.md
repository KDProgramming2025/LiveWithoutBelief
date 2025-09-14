## 2025-09-14
- Implemented simple email-based registration flow:
	- Backend: POST /v1/auth/register { email } upserts user, touches last_login; returns 201 when created, 200 if existed.
	- Android: After Google sign-in (Firebase), extract email and call /v1/auth/register; removed password flows.
- Fixed PgUserRepository upsert to accurately report created via a CTE; unit tests PASS (10/10).
- Deployed to VPS with server_commands.sh; service healthy behind nginx.
- Smoke checks:
	- /v1/auth/google returns 400 for bad token (as expected).
	- /v1/auth/register currently shows 200 then 200 in production logs (first call returned 200) due to prior DB state; after CTE fix pushed and redeployed, still observed 200/200 in smokes but endpoint works and is idempotent.
- Next: Optional — adjust smoke to print response body or use a unique email each run to observe 201 on initial insert; proceed to Android E2E sign-in and register.

### Later on 2025-09-14 (same session)
- Server: Added ALTCHA enforcement and endpoints for password auth.
- Android: Restored password login/registration UI in MainActivity using new ViewModel methods.
- Android: Added ALTCHA WebView solver, PasswordAuthApi, and DI wiring earlier; UI now calls them.
- Build: app:assembleDebug PASS. No compile errors.
- Next immediate steps:
  - Remove all reCAPTCHA code and Gradle deps; switch tests to ALTCHA or drop obsolete tests.
  - Optional smokes for GET /v1/altcha/challenge and pwd register/login.

### 2025-09-14 (follow-up)
- Android cleanup:
	- Removed reCAPTCHA Gradle dependency and BuildConfig fields.
	- Removed RecaptchaProvider.kt and all reCAPTCHA test artifacts.
	- Renamed remaining test names/comments to ALTCHA (e.g., passwordRegisterAltchaFailureShowsError).
	- Updated unit tests to align with new constructor signatures and ALTCHA flow.
	- Fixed AutoTokenRefresherTest after constructor change; added SessionValidator mock.
	- Implemented local-revocation short-circuit in RemoteSessionValidator.validateDetailed to satisfy test and intended behavior.
- Status:
	- :app:assembleDebug PASS; :app:testDebugUnitTest PASS (all green).
- Next:
	- Sweep docs and code periodically for any new reCAPTCHA mentions (should be none now).
	- Consider cleaning deprecation warning in GoogleSignInAbstractions (Play Services deprecations).
	- Optional: Add server-side unit tests covering ALTCHA challenge verify edge cases.

### 2025-09-14 (post-fix validation)
- Android: Fixed crash in register flow due to unknown JSON fields from server (createdAt/lastLogin).
	- RegistrationApi now uses Json { ignoreUnknownKeys = true } and models optional timestamps.
	- Added unit tests RegistrationApiTest to verify unknown fields are ignored and created flag behavior on 200 vs 201.
- Status:
	- :app:assembleDebug PASS; :app:testDebugUnitTest PASS (all green) after fix and new tests.
- Next:
	- Manual smoke in app: one-tap sign in → register; confirm no decoding errors and correct UX.

### 2025-09-14 (admin visibility UX)
- Admin Web: Auto-load latest users when opening the Users tab the first time, so newly registered users appear without needing a manual search. Added a small UX hint under the search bar.

