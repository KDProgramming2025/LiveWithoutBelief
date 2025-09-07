Recent snapshot:
* Refactored One Tap provider (CredentialCall abstraction) with passing unit test.
* Added AuthFacade tests covering silent, one-tap, and interactive fallback branches.
* Added in-memory SecureStorage test (round-trip token/profile/expiry & clear).
* Added fail-fast guard for placeholder GOOGLE_SERVER_CLIENT_ID (ServerClientIdGuard) in DI.
* Refactored AutoTokenRefresher to use injectable TokenRefreshConfig; updated tests.
* Added ValidationObserver hook to RemoteSessionValidator (Noop bound via DI).
* Added RevocationStore (in-memory) with local short-circuit validation + test.
* Added ValidationObserver test (retry events) with relaxed assertions.
* JWT exp parsing + AutoTokenRefresher tests green.

Latest:
* Added PrefsRevocationStore (persistent hashed tokens) and bound via DI replacing in-memory default.
* Added persistence test verifying revocation survives new instance.

Upcoming (auth focus):
1. Telemetry/logging wiring for ValidationObserver.
2. Externalize retry / refresh configs (remote or BuildConfig gating).
3. Additional edge-case tests (multiple consecutive revokes, observer error handling).