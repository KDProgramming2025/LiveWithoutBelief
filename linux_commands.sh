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

echo "[STEP] Sync to remote branch (force-clean if needed)"
git --no-pager status -sb || true
git fetch origin feature/LWB-92-admin-ui || true
git reset --hard origin/feature/LWB-92-admin-ui || true
git clean -fd || true

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

echo "[STEP] Build Admin API"
cd /var/www/LWB/LiveWithoutBelief/admin/api
npm ci --no-audit --no-fund
npm run build

echo "[STEP] Deploy Admin API dist to /opt/lwb-admin-api and restart service"
mkdir -p /opt/lwb-admin-api
rsync -ah --delete dist/ /opt/lwb-admin-api/
systemctl restart lwb-admin-api.service || true
sleep 1

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

echo "[DIAG] Test menu icon edit end-to-end (login + upload)"
# Source env if present to get ADMIN_PANEL_USERNAME/ADMIN_PANEL_PASSWORD without echoing them
if [ -f /etc/lwb-server.env ]; then
	# shellcheck disable=SC1091
	. /etc/lwb-server.env || true
fi
API_ORIGIN="https://aparat.feezor.net"
API_BASE="$API_ORIGIN/LWB/Admin/api"

# Create a non-placeholder test file (>68 bytes) into /tmp/menu-test.png (not a valid PNG but good for size check)
dd if=/dev/zero of=/tmp/menu-test.png bs=1 count=256 status=none || true

# Login to admin and store cookie (avoid printing credentials)
COOKIE_JAR="/tmp/lwb-admin-cookies.txt"
rm -f "$COOKIE_JAR" || true
if [ -n "$ADMIN_PANEL_USERNAME" ] && [ -n "$ADMIN_PANEL_PASSWORD" ]; then
	# Enable debug logs for multipart and menu for this run
	export DEBUG_MULTIPART=1
	export DEBUG_MENU=1
	curl -sS -c "$COOKIE_JAR" -H "Content-Type: application/json" \
		-d "{\"username\":\"$ADMIN_PANEL_USERNAME\",\"password\":\"$ADMIN_PANEL_PASSWORD\"}" \
		"$API_BASE/v1/admin/login" >/dev/null || true
		# Upload new icon for menu-1 if it exists
	if [ -f /var/www/LWB/Menu/menu-1/icon.png ] || [ -d /var/www/LWB/Menu/menu-1 ]; then
		echo " - Uploading test icon to menu-1"
			NEW_TITLE="menu 1 $(date +%s)"
			curl -sS -b "$COOKIE_JAR" \
				-F "title=$NEW_TITLE" \
				-F "icon=@/tmp/menu-test.png;type=image/png" \
				"$API_BASE/v1/admin/menu/menu-1/edit" >/dev/null || true
			# Show resulting file size and timestamp
			if ls -1 /var/www/LWB/Menu/menu-1/icon.* >/dev/null 2>&1; then
				for f in /var/www/LWB/Menu/menu-1/icon.*; do
					echo "   -> $(basename "$f"): $(stat -c '%s bytes, %y' "$f" || echo 'stat failed')"
				done
			fi
			# Show last 30 lines of API logs for debug output
			journalctl -u lwb-admin-api.service -n 30 --no-pager || true
	else
		echo " - Skipping upload: menu-1 directory not found"
	fi
else
	echo " - Skipping login/upload: ADMIN_PANEL_USERNAME/PASSWORD not available"
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
	echo " - Canonical (with cache-bust)"
	curl -Is "$CANON?v=$(date +%s)" | sed -n '1,10p'
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
