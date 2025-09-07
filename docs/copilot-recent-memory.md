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
* Added sampling + snapshot export observers, resilience to delegate exceptions, extended tests.

Upcoming (auth focus):
1. README / docs for auth config knobs & observer pipeline.
2. Potential persistent metrics queue (offline flush).
3. Multi-process safety review for PrefsRevocationStore.