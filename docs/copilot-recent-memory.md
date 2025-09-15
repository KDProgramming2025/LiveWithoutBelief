Context (last updated: deploy success)
- Admin Web: Articles are cards; change cover/icon actions show busy overlay + toasts; cache-busting ?v=updatedAt applied to images.
- Admin API: Major refactor completed. Monolithic src/index.ts split into modular layers (config, types, utils, security, repositories, services, routes). Functionality preserved, files <600 lines, SOLID/MVVM friendly.
- Utilities: writeFileAtomic, ensureDirSync, slugify, getImageExtFromNameOrMime, readPartBuffer.
- Repositories: ArticleRepository (metadata JSON + scan), UserRepository (Postgres-backed, fallback when DATABASE_URL missing).
- Services: ArticleService (list/move/edit/upload/reindex), UserService.
- Routes: auth, articles, users; wired in new index.ts composition.
- Deploy: Same as before (Admin Web at /var/www/LWB/Admin; Admin API at /opt/lwb-admin-api; nginx as configured).

Known state
- Local build succeeded (tsup). Tests passed (vitest).
- Refactor committed (latest commit ceaea0f on feature/LWB-92-admin-ui).
- Deployment executed successfully via ephemeral script (auto-stash added).
- Remote probes: local API /v1/admin/session -> {"authenticated":false}; public proxy -> {"authenticated":false}.

Next
- Future: review large bundle size (858kB) -> investigate code splitting / dynamic imports.
- Optional: npm audit moderate vulnerabilities (Admin Web) - consider selective upgrades.
- Add integration test hitting /v1/admin/session mock (already returns authenticated false when no cookie).