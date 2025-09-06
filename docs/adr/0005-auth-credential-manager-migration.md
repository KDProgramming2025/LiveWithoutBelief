# ADR 0005: Google One-Tap / Credential Manager Migration Strategy

Date: 2025-09-06
Status: Accepted (Interim)

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

Temporarily retain a stub inside `FirebaseCredentialAuthFacade.oneTapSignIn` that throws a dedicated
`UnsupportedOperationException` until the new Credential Manager based flow is implemented. We will create a
separate Jira sub-task (to be filed) to:

* Investigate current recommended approach (either updated Credential Manager Google ID option or fallback to
  Google Identity Services via `BeginSignInRequest` / `SignInClient`).
* Potentially refactor the facade to a two-phase intent-based flow if required by the modern API:
  - `prepareSignIn(): SignInLaunchHandle` (contains an IntentSender or Intent).
  - `completeSignIn(resultIntent: Intent): Result<AuthUser>`.
* Add instrumentation tests exercising full flow with a fake backend validator and an emulated Google account.

## Consequences

* Current debug builds will show an error state after tapping sign-in (pending implementation) — acceptable in the
  short term while we finalize API approach.
* Unit tests remain green; no test currently relies on the concrete 1-tap implementation details.
* Risk of forgetting the stub mitigated by adding the Jira sub-task and referencing this ADR.

## Alternatives Considered

1. Downgrade to `1.2.0-rc01`: Attempted but the required classes still not resolved in our environment (likely
   artifact or signature changes + dependency convergence forcing 1.3.0). Rejected to avoid dependency churn.
2. Immediate refactor to Google Identity Services (GMS) intent flow: Larger surface change; deferred to keep small
   increments.
3. Implement anonymous Firebase auth as placeholder: Would not satisfy product goal of Google account identity.

## Migration Outline (Target Sub-task)

1. Research current Credential Manager Google sign-in option name(s) (GetGoogleIdOption or equivalent) + sample usage.
2. Adjust `AuthFacade` either to keep a one-shot suspend (if API supports synchronous token return) or introduce a
   two-step intent launch abstraction.
3. Implement new concrete flow with proper error mapping (cancellation, no credentials, network, developer error).
4. Persist ID token & profile (already implemented; reuse existing storage calls).
5. Add instrumentation test on emulator (API 34+) validating happy path + cancellation path.
6. Update README + this ADR with final API names and mark ADR status = Superseded.

## Testing Impact

* Additional unit tests will mock the new abstraction once finalized.
* Instrumentation layer will need a small test utility to drive ActivityResult.

## References (internal only)

* `AuthFacade.kt` – location of temporary stub.
* `AuthViewModel.kt` – UI state machine that will remain unchanged after migration (only underlying facade changes).

---
Next action: Create Jira sub-task (LWB-XX) referencing this ADR and implement migration in a dedicated branch.
