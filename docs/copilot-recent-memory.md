Context (last updated: E2E icon upload fixed + verification)

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

2025-09-16
- Problem: Admin Web “change icon” looked like it saved but icon stayed as 68–71 bytes on disk; server logs showed multipart size 0 via proxy and edit 404 for wrong ids.
- Fixes shipped:
	- Menu routes: added saveRequestFiles fallback in add/edit to handle empty attachFieldsToBody streams (nginx path).
	- E2E script (linux_commands.sh): end-to-end deploy + login + create menu if missing + upload via proxy and (if cookie token present) direct local, then verify.
	- Script now prints HTTP HEAD for canonical icon URL to assert Content-Length and Last-Modified, and prints disk stat for icon.*.
	- Rsync refined to avoid deleting runtime data directory.
- Verified on server:
	- Proxy edit updated icon to 256 bytes; HTTP HEAD Content-Length: 256, Last-Modified updated; disk stat shows 256 bytes; menu.json updated.
	- Direct local edit skipped in this run due to cookie token not parsed from curl jar (still acceptable since proxy path now works end-to-end).
- Next:
	- Optionally improve token extraction for localhost tests (set-cookie vs cookie-jar nuance) and add a small API integration test.

2025-09-16 (later)
- Symptom: Only one menu item visible; suspected earlier rsync removed /opt/lwb-admin-api/data/menu.json. Icons folders existed.
- Action: Extended linux_commands.sh to reconcile metadata from /var/www/LWB/Menu folder names, creating missing items with inferred title/label and sequential order. Kept runtime data safe in rsync.
- Result: Recovered items menu-1 and menu-2 with icons; menu.json now contains [home, menu-1, menu-2]; Admin API list returns 3 items; proxy icon uploads continue to work (256B).

2025-09-16 (latest)
- UX: Removed all icon cache-busting query params from Admin Web (menu and articles). If an image fails once, we show a placeholder instead of retry loops.
- UX: Added inline spinner directly in the icon slot while uploads are in progress (menu and article icons), on top of the button state/overlay.
- Next: Rebuild Admin Web on the server and redeploy to apply changes; verify icons load without ?v= params and that spinners appear in-place during icon change.