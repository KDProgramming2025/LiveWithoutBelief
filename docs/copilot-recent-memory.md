Context (last updated: now)
- Admin Web: Articles are cards; change cover/icon actions show busy overlay + toasts; cache-busting ?v=updatedAt applied to images.
- Admin API: Edit route hardened to read files via attachFieldsToBody, saveRequestFiles, and req.parts() stream; cleans stale ext variants; only bumps updatedAt when changed; adds write logs. New: image validation (sniff headers, completeness) and atomic writes (temp+rename), canonicalizes extensions. Strengthened WEBP completeness check (verify RIFF size and presence of VP8/VP8L/VP8X) to reject truncated icons.
- Deploy: Admin Web at /var/www/LWB/Admin (latest bundle), Admin API at /opt/lwb-admin-api (service active). Diagnostics script available via server_commands.sh (restarts service, tails logs, lists recent assets).

Known state
- Previous attempts updated updatedAt but image files did not change (mtimes unchanged).
- After latest deploy, server logs show writes on fallback path and service healthy. Need real UI attempt with new validation in place.

Next
- Perform a cover/icon change in Admin Web; capture the article slug.
- Re-run diagnostics to confirm file write logs and updated mtimes; if warnings returned (image_unrecognized/incomplete/upload_truncated), surface them in UI.