## 2025-09-14
- Refactored server auth to SOLID modules (auth/domain, repository, service, controller) and removed legacy endpoints (/v1/auth/validate, /v1/auth/revoke, /v1/auth/register, /v1/auth/login, /v1/auth/pwd/validate).
- New endpoint: POST /v1/auth/google { idToken } returning { user, profile }.
- buildServer now requires DATABASE_URL in production; tests use InMemoryUserRepository.
- Updated unit tests to the new flow; all tests PASS locally.
- Deployed code to VPS, but server_commands.sh halted because /etc/lwb-server.env lacks DATABASE_URL; awaiting the actual postgres connection string to proceed.
- Next: add DATABASE_URL to /etc/lwb-server.env, rerun server_commands.sh to build/restart, then verify POST /v1/auth/google works from Android.

