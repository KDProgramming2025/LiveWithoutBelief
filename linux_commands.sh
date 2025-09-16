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
