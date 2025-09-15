Context (last updated: now)
- Admin Web: Articles are cards; change cover/icon actions show busy overlay + toasts; cache-busting ?v=updatedAt applied to images.
- Admin API: Edit route hardened to read files via attachFieldsToBody, saveRequestFiles, and req.parts() stream; cleans stale ext variants; only bumps updatedAt when changed; adds write logs.
- Deploy: Admin Web at /var/www/LWB/Admin (latest bundle), Admin API at /opt/lwb-admin-api (service active). Diagnostics script available via server_commands.sh.

Known state
- Previous attempts updated updatedAt but image files did not change (mtimes unchanged).
- After latest deploy, no new edit attempt performed yet; metadata file may be absent until next write.

Next
- Perform a cover/icon change in Admin Web; capture the article slug.
- Re-run diagnostics to confirm file write logs and updated mtimes; if still failing, log multipart field map and returned filenames for deeper triage.