# ADR 0005: Google One-Tap / Credential Manager Migration Strategy

Date: 2025-09-06
Status: Superseded

## Context

We attempted to integrate Google One-Tap using the AndroidX Credential Manager library. The previously used types
`GetSignInCredentialOption` and `SignInCredential` (early API surface) are not present in the currently resolved
artifacts (1.3.0). The structure of the 1.3.0 AAR indicates internal controller classes but no public option class
matching those original names. The public API set exposed in `androidx.credentials:credentials:1.3.0` only includes
generic option types (password, public-key, custom) and no direct Google sign-in convenience type. This mismatch
caused compilation failures.

Our goals:
1. Provide a frictionless single-tap (no text input) Google sign-in.
2. Preserve a clean `AuthFacade` abstraction for UI / feature modules.
3. Maintain testability (unit + future instrumentation) and avoid leaking implementation details.
4. Avoid blocking the broader feature delivery while researching the updated API surface.

## Decision

Implemented migration by abandoning Credential Manager for now and adopting Google Identity Services sign-in
with a silent-first + interactive fallback strategy. Added test seams (`GoogleSignInClientFacade`,
`GoogleSignInIntentExecutor`, `SignInExecutor`, `TokenRefresher`) enabling unit testing without real Tasks.
Instrumentation test covers interactive launch smoke.

## Consequences

* Users now experience immediate silent sign-in when cached credentials exist; otherwise an intent-based flow.
* Auth module unit tests validate silent path and refresh logic via abstractions (no hanging).
* Interactive path validated via instrumentation (best-effort smoke w/ emulator account).

## Alternatives Considered

1. Downgrade to `1.2.0-rc01`: Attempted but the required classes still not resolved in our environment (likely
   artifact or signature changes + dependency convergence forcing 1.3.0). Rejected to avoid dependency churn.
2. Immediate refactor to Google Identity Services (GMS) intent flow: Larger surface change; deferred to keep small
   increments.
3. Implement anonymous Firebase auth as placeholder: Would not satisfy product goal of Google account identity.

## Migration Outline (Target Sub-task)

Superseded migration plan: Completed via Google Identity Services; reintroducing Credential Manager may become a
future ADR if the ergonomic benefit outweighs added complexity.

## Testing Impact

* Unit tests mock sign-in & token refresh using injected executors.
* Instrumentation layer validates interactive launch path; future enhancement could assert UI state.

## References (internal only)

* `AuthFacade.kt` – location of temporary stub.
* `AuthViewModel.kt` – UI state machine that will remain unchanged after migration (only underlying facade changes).

---
No further action for this ADR; closed.
