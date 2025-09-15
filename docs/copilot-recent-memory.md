Context (last updated: Admin Web icon <img> deploy)

2025-09-15
- Admin Web: replaced browser prompts with MUI Dialogs (Confirm/SingleField/TwoField) across Menu, Articles, Users, Upload flows. Built and deployed successfully to server via server_commands.sh. Chunk ~873kB; non-blocking warning.
- Backend already supports Menu CRUD incl. edit; API service running and healthy; session probe unauthenticated as expected.
- Next: optional bundle split, clarify/remove stray admin/web/src/App.js, and continue Menu UX refinements if requested.

- Remote probes: local API /v1/admin/session -> {"authenticated":false}; public proxy -> {"authenticated":false}.

- New: Menu cards now render explicit <img> tags for icons with URL normalization and single-retry on error. Built and deployed. Verify DOM contains <img> under MuiCard-root and that edit uses MUI dialog (no browser prompts).

- Fix: Admin API MenuService.list backfills missing icon URLs by scanning Menu public dir when icon files exist (png/jpg/jpeg/webp). Added unit test test/menuService.test.ts.

Next
- Future: review large bundle size (858kB) -> investigate code splitting / dynamic imports.
- Optional: npm audit moderate vulnerabilities (Admin Web) - consider selective upgrades.
- Add integration test hitting /v1/admin/session mock (already returns authenticated false when no cookie).