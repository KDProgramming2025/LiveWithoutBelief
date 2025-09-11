Date: 2025-09-11
Branch: feature/LWB-65-reader-ui (active)

Current State Summary:
- Core Reader features (LWB-66..72) implemented & stable: parsing, pagination, settings, media (audio/YouTube), search (SearchHit + auto-scroll + active highlight), TOC navigation, light/dark previews.
- Parser structural test and edge-case tests present (whitespace test partially relaxed pending stricter cleanup refinement).

Immediate Next Steps (post LWB-65 completion - optional refinements):
1. Tighten whitespace paragraph test once parser normalization improved.
2. Add nested heading & mixed media ordering tests.
3. Enhance audio UI (dedicated pause icon asset) if design requires.

Deferred / Future:
- Multi-language content handling.
- Annotation/bookmark discussion threads backend integration.
- Performance tuning for very large documents (streaming/async parsing).

Keep File Lean: remove completed items once verified after adding new tests.
