#!/usr/bin/env bash
set -euo pipefail

# Rebuild Admin Web and deploy static assets
# Environment specifics:
# - Node is isolated at /opt/lwb-node/current
# - Repo path on server: /var/www/LWB/LiveWithoutBelief
# - Admin Web path: admin/web
# - Public web root for Admin Web: /var/www/LWB/Admin/Web

export PATH="/opt/lwb-node/current/bin:$PATH"

echo "[STEP] Node version"
node -v
npm -v || true

echo "[STEP] Navigate to repo"
cd /var/www/LWB/LiveWithoutBelief

echo "[STEP] Pull latest commits for current branch"
git --no-pager status -sb
git pull --ff-only || true

echo "[STEP] Install deps (admin/web)"
cd admin/web
npm ci --no-audit --no-fund

echo "[STEP] Build admin web"
npm run build

echo "[STEP] Deploy dist -> /var/www/LWB/Admin/Web"
mkdir -p /var/www/LWB/Admin/Web
rsync -ah --delete dist/ /var/www/LWB/Admin/Web/

echo "[DONE] Admin Web deployed. Sample index:"
ls -lah /var/www/LWB/Admin/Web | sed -n '1,50p'

echo "[DIAG] List existing menu icon files"
shopt -s nullglob
for f in /var/www/LWB/Menu/*/icon.*; do
	echo " - $(basename $(dirname "$f"))/$(basename "$f") => $(stat -c '%s bytes, %y' "$f" || echo 'stat failed')"
done

echo "[DIAG] Check bundle contains canonical '/LWB/Admin/Menu'"
JS_BUNDLE=$(ls -1 /var/www/LWB/Admin/Web/assets/index-*.js 2>/dev/null | head -n1 || true)
if [ -n "$JS_BUNDLE" ]; then
	echo " - Bundle: $JS_BUNDLE"
	grep -q "/LWB/Admin/Menu" "$JS_BUNDLE" && echo "   FOUND canonical path in bundle" || echo "   NOT FOUND canonical path in bundle"
else
	echo " - No JS bundle found under /var/www/LWB/Admin/Web/assets"
fi

echo "[DIAG] Probe canonical vs legacy icon URLs (first found)"
FIRST_ICON=$(ls -1 /var/www/LWB/Menu/*/icon.* 2>/dev/null | head -n1 || true)
if [ -n "$FIRST_ICON" ]; then
	ID=$(basename "$(dirname "$FIRST_ICON")")
	NAME=$(basename "$FIRST_ICON")
	CANON="https://aparat.feezor.net/LWB/Admin/Menu/$ID/$NAME"
	LEGACY="https://aparat.feezor.net/LWB/Menu/$ID/$NAME"
	echo " - Canonical: $CANON"
	curl -Is "$CANON" | sed -n '1,10p'
	echo " - Legacy: $LEGACY"
	curl -Is "$LEGACY" | sed -n '1,10p'
else
	echo " - No icon files found to probe"
fi

echo "[DIAG] Current menu metadata file"
if [ -f /opt/lwb-admin-api/data/menu.json ]; then
	echo " - /opt/lwb-admin-api/data/menu.json exists; head:"
	head -n 200 /opt/lwb-admin-api/data/menu.json | sed -n '1,200p'
else
	echo " - menu.json not found at /opt/lwb-admin-api/data/menu.json"
fi

echo "[DIAG] Permissions for /var/www/LWB/Menu"
ls -ld /var/www/LWB/Menu
ls -l /var/www/LWB/Menu | sed -n '1,200p'

echo "[DIAG] Admin API service status/logs (best-effort)"
SYSTEMD_UNIT="lwb-admin-api.service"
systemctl status "$SYSTEMD_UNIT" --no-pager || true
journalctl -u "$SYSTEMD_UNIT" -n 50 --no-pager || true
