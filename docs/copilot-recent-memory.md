Context (last updated: now)
- Admin Web: Articles are cards; change cover/icon actions show busy overlay + toasts; cache-busting ?v=updatedAt applied to images.
- Admin API: Major refactor completed. Monolithic src/index.ts split into modular layers (config, types, utils, security, repositories, services, routes). Functionality preserved, files <600 lines, SOLID/MVVM friendly.
- Utilities: writeFileAtomic, ensureDirSync, slugify, getImageExtFromNameOrMime, readPartBuffer.
- Repositories: ArticleRepository (metadata JSON + scan), UserRepository (Postgres-backed, fallback when DATABASE_URL missing).
- Services: ArticleService (list/move/edit/upload/reindex), UserService.
- Routes: auth, articles, users; wired in new index.ts composition.
- Deploy: Same as before (Admin Web at /var/www/LWB/Admin; Admin API at /opt/lwb-admin-api; nginx as configured).

Known state
- Local build succeeded (tsup). Tests passed (vitest).
- Refactor committed. Deployment pending manual scp+ssh (ephemeral helper script not tracked).

Next
- Deploy steps (never batch):
	1) scp server_commands.sh lwb-server:/tmp/server_commands.sh
	2) ssh lwb-server "bash /tmp/server_commands.sh"
	(Script sets DEPLOY_BRANCH=feature/LWB-92-admin-ui and calls server/deploy.sh on server.)
- Post-deploy validation: systemctl status lwb-admin-api.service, curl /v1/admin/session via public proxy.