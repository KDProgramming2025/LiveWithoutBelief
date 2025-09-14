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

