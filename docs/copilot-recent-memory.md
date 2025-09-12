Date: 2025-09-12
Branch: feature/LWB-73-bookmarks (active)

Current State Summary:
- Bookmarks data layer: BookmarkRepositoryImpl now implements add/remove, createFolder (idempotent), moveBookmark, and searchBookmarked; DAO methods and mappings in place.
- Tests: data:repo unit tests cover add/remove and new folder/move/search behaviors; all passing.
- Feature UI: Bookmarks screen lists bookmarks, can add/remove, create folders, move bookmarks to a folder, and search bookmarked articles.
- DI: App module provides use cases for bookmarks (get/add/remove/folders/create/move/search). Domain use cases added.

Immediate Next Steps:
1. Integrate Bookmarks screen into app navigation.
2. Preserve createdAt on move (add DAO getById or update query) — optional refinement.
3. Add UI tests (compose) for folder creation, move, and search.

Deferred / Future:
- Folder filtering UX and displaying article titles in bookmark rows.
- Server sync for bookmarks when backend is ready.

Keep File Lean: prune done items as they land in main.
