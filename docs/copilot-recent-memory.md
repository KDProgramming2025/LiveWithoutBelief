Date: 2025-09-10
Branch: feature/LWB-53-client-sync

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
- LWB-48: Implemented and deployed with Postgres; endpoints for manifest and article details. PR #14 green after lint fixes.
- LWB-53: Created branch feature/LWB-53-client-sync. Android: added ArticleDto/SectionDto/MediaDto and getArticle() in network; implemented ArticleRepositoryImpl.refreshArticles() to fetch manifest and per-article content with text hash delta; unit tests added and passing for repo module.

Next:
- Finish LWB-53: add more tests as needed; wire periodic sync later if in scope; open PR.
