Date: 2025-09-10
Branch: feature/LWB-47-search

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

Next:
- Implement LWB-47: Local Android search only (no server). Added DAO LIKE-based search, repo API searchLocal, Hilt VM, and Compose screen in feature/search. Wire into app nav next.
