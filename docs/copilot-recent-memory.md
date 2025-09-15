Context (last updated: now)
- Admin Web: Articles are cards; change cover/icon actions show busy overlay + toasts; cache-busting ?v=updatedAt applied to images.
- Admin API: Edit route hardened to read files via attachFieldsToBody, saveRequestFiles, and req.parts() stream; cleans stale ext variants; only bumps updatedAt when changed; adds write logs. New: image validation (sniff headers, completeness) and atomic writes (temp+rename), canonicalizes extensions. Strengthened WEBP completeness check (verify RIFF size and presence of VP8/VP8L/VP8X) to reject truncated icons.
- Deploy: Admin Web at /var/www/LWB/Admin (latest bundle), Admin API at /opt/lwb-admin-api (service active). Nginx for /LWB/Admin/api has proxy_request_buffering on. Nginx for /LWB/Articles now disables range requests (max_ranges 0). A diagnostic server_commands.sh now curls a sample image with and without Range to verify 200 OK and no Accept-Ranges.

Known state
- Previous attempts updated updatedAt but image files did not change (mtimes unchanged).
- After latest deploy, server logs show writes on fallback path and service healthy. Need real UI attempt with new validation in place.

Next
- Have the user retest image loads after upload and confirm no net::ERR_HTTP2_PROTOCOL_ERROR 206. If persists, capture the exact image URL and timestamp; check nginx error/access logs and compare Content-Length vs file size.