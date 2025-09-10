Date: 2025-09-10
Branch: main

Completed (server):
- LWB-51: Signed manifest builder (checksum + HMAC) implemented; optional manifest returned by /v1/ingest/docx when MANIFEST_SECRET is set.
- LWB-52: Sanitized output shape verified; ingestion parsing uses sanitize-html; tests green.
- Tests: 11/11 passing (vitest).

Notable stubs/placeholders (outside 51/52 scope):
- /v1/articles/manifest returns static sample items (placeholder endpoint).
- In ingestion response, manifest uses id/title "upload" for now (placeholder identifiers; no persistence yet).

Notes:
- No TODO/FIXME markers in server/src related to LWB-46/51/52.
- MANIFEST_SECRET gates manifest emission by design.

Recent:
- LWB-47 PR merged; local cleanup complete.
- Started LWB-48 on branch feature/LWB-48-persist. Implemented server persistence layer with in-memory/PG store, wired ingestion to save article, added endpoints: GET /v1/articles/manifest and GET /v1/articles/:id. All server tests pass (12/12).

Next:
- Decide on immediate task: lightweight tests for SearchViewModel or start next Jira ticket.
