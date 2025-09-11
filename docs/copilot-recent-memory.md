Date: 2025-09-11
Branch: feature/LWB-65-reader-ui (active)

Current State Summary:
- Core Reader features (LWB-66..72) implemented & stable: parsing, pagination, settings, media (audio/YouTube), search (SearchHit + auto-scroll + active highlight), TOC navigation, light/dark previews.
- Parser structural test and edge-case tests present (whitespace test partially relaxed pending stricter cleanup refinement).
- CI mirror locally: all unit tests green (debug+release), dependency guard passes, quality task passes. Prior CI failure in data:repo ArticleRepositoryImplTest (release) is not reproducible locally now.

Immediate Next Steps (post LWB-65 completion - optional refinements):
1. Tighten whitespace paragraph test once parser normalization improved.
2. Add nested heading & mixed media ordering tests.
3. Enhance audio UI (dedicated pause icon asset) if design requires.

Notes (2025-09-11):
- Investigated CI failure (ArticleRepositoryImplTest.refreshArticles_syncsManifestItems under release). Re-ran targeted tests and full quality locally; all green. If CI re-fails, fetch test report artifact and compare env (JDK 17 vs 21, AGP, caching). No code changes required now.

Deferred / Future:
- Multi-language content handling.
- Annotation/bookmark discussion threads backend integration.
- Performance tuning for very large documents (streaming/async parsing).

Keep File Lean: remove completed items once verified after adding new tests.
