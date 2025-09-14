Context

 Recent: Deployed build with detailed /v1/auth/validate logs and GOOGLE_ALLOWED_AUDIENCES. Tailed VPS logs and confirmed:
  - validate 200 with google_validate_link:created for mohammadmdp@gmail.com at ~21:31:50 and again at ~22:40:43 (user created successfully).
  - Some validate 401 cases show google_bypass_aud_mismatch (aud="WRONG_AUDIENCE"), indicating occasional wrong client ID presented; env already includes Android client ID.
  - Earlier failures attempted to fetch Google certs (403) before bypass was active; now bypass logs are visible and linkage works.
 Next: Have user retry Android sign-in; expect 200 and user visible in Admin Users. If 401 persists, capture timestamp and compare aud/azp vs configured IDs.
