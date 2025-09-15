Context (last updated: deploy success)

2025-09-15
- Admin Web: replaced browser prompts with MUI Dialogs (Confirm/SingleField/TwoField) across Menu, Articles, Users, Upload flows. Built and deployed successfully to server via server_commands.sh. Chunk ~873kB; non-blocking warning.
- Backend already supports Menu CRUD incl. edit; API service running and healthy; session probe unauthenticated as expected.
- Next: optional bundle split, clarify/remove stray admin/web/src/App.js, and continue Menu UX refinements if requested.

- Remote probes: local API /v1/admin/session -> {"authenticated":false}; public proxy -> {"authenticated":false}.

Next
- Future: review large bundle size (858kB) -> investigate code splitting / dynamic imports.
- Optional: npm audit moderate vulnerabilities (Admin Web) - consider selective upgrades.
- Add integration test hitting /v1/admin/session mock (already returns authenticated false when no cookie).