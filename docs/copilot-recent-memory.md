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
* Added LoggingValidationObserver + CompositeValidationObserver with fan-out test.
* Added dynamic BuildConfig-based retry/refresh tuning & MetricsValidationObserver + test.

Upcoming (auth focus):
1. Sampling & structured export for metrics observer.
2. Edge-case tests: multiple consecutive revokes, forced composite exception handling.
3. Documentation update for new BuildConfig tuning knobs.