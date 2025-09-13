Context

- Server validate endpoint updated to log validate_start, google_bypass_accepted_via_aud/azp, and no-identity errors. Deployed to VPS and health is OK.

Next step

- Re-attempt Google sign-in from the Android app. If it returns 401, immediately fetch server logs for events: validate_start, google_bypass_* and request completed lines to pinpoint cause, then patch accordingly.
