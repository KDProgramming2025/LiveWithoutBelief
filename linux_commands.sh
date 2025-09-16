#/usr/bin/env bash
#set -euo pipefail

# Single-step E2E: login, get menu id, upload icon (proxy and direct), print diagnostics

API_PROXY="https://aparat.feezor.net/LWB/Admin/api"
API_LOCAL="http://127.0.0.1:5050"
ICON_FILE="/tmp/menu-test.png"

# Create a tiny but non-empty PNG for testing (1x1 transparent)
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\x0cIDAT\x08\xd7c``\xf8\xff\xff?\x00\x05\xfe\x02\xfeA\x90\x80\xf0\x00\x00\x00\x00IEND\xaeB`\x82' > "$ICON_FILE"

# Ensure ADMIN creds are available from env file if present
: "${ADMIN_USER:=${ADMIN_USER:-}}"
: "${ADMIN_PASS:=${ADMIN_PASS:-}}"

if [ -z "${ADMIN_USER}" ] || [ -z "${ADMIN_PASS}" ]; then
	echo "Missing ADMIN_USER/ADMIN_PASS in env; export before running." >&2
	exit 1
fi

# 1) Login via proxy and capture cookie
COOKIE_JAR="/tmp/lwb_admin_cookies.txt"
rm -f "$COOKIE_JAR"

curl -sS -k -c "$COOKIE_JAR" -H "Content-Type: application/json" \
	-d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
	"$API_PROXY/v1/admin/login" | cat

echo

# 2) Get a valid menu id
MENU_ID=$(curl -sS -k -b "$COOKIE_JAR" "$API_PROXY/v1/admin/menu" | sed -n 's/.*"id":"\([^"]\+\)".*/\1/p' | head -n1)
if [ -z "$MENU_ID" ]; then
	echo "No menu id found via API; falling back to filesystem" >&2
	MENU_ID=$(ls -1 /var/www/LWB/Menu 2>/dev/null | head -n1 || true)
fi
if [ -z "$MENU_ID" ]; then
	echo "No MENU_ID available" >&2
	exit 1
fi

echo "Using MENU_ID=$MENU_ID"

# 3) Upload icon through proxy
curl -sS -k -b "$COOKIE_JAR" -F "icon=@$ICON_FILE;type=image/png" "$API_PROXY/v1/admin/menu/$MENU_ID/edit" | cat

echo

# 4) Upload icon directly to local port (bypass nginx) to compare
curl -sS -b "$COOKIE_JAR" -F "icon=@$ICON_FILE;type=image/png" "$API_LOCAL/v1/admin/menu/$MENU_ID/edit" | cat

echo

# 5) Print on-disk files and sizes
ls -la "/var/www/LWB/Menu/$MENU_ID" || true
stat "/var/www/LWB/Menu/$MENU_ID"/icon.* 2>/dev/null || true

# 6) Tail recent admin api logs for multipart debug
journalctl -u lwb-admin-api -n 120 --no-pager | tail -n 120 | sed -n 's/.*DEBUG.*//p'
#!/usr/bin/env bash
set -euo pipefail

# Single-step: login, upload a test icon for the first existing menu ID, print resulting file size, and show recent API logs.
API_ORIGIN="https://aparat.feezor.net"
API_BASE="$API_ORIGIN/LWB/Admin/api"
COOKIE_JAR="/tmp/lwb-admin-cookies.txt"
rm -f "$COOKIE_JAR" || true

# Load credentials (if available) and ensure debug toggles are active for this run
if [ -f /etc/lwb-server.env ]; then . /etc/lwb-server.env || true; fi
export DEBUG_MENU=1
export DEBUG_MULTIPART=1

# Login (silently)
curl -sS -c "$COOKIE_JAR" -H "Content-Type: application/json" \
	-d "{\"username\":\"$ADMIN_PANEL_USERNAME\",\"password\":\"$ADMIN_PANEL_PASSWORD\"}" \
	"$API_BASE/v1/admin/login" >/dev/null || true

# Resolve a menu ID to target: pick first directory under /var/www/LWB/Menu
MENU_ID=""
FIRST_DIR=$(ls -1d /var/www/LWB/Menu/* 2>/dev/null | head -n1 || true)
if [ -n "$FIRST_DIR" ]; then MENU_ID=$(basename "$FIRST_DIR"); fi
if [ -z "$MENU_ID" ]; then echo "No menu directories found under /var/www/LWB/Menu"; exit 1; fi
echo "Using MENU_ID=$MENU_ID"

# Create a >68B test file and upload it as the new icon
dd if=/dev/zero of=/tmp/menu-test.png bs=1 count=256 status=none || true
curl -sS -b "$COOKIE_JAR" \
	-F "title=$MENU_ID debug $(date +%s)" \
	-F "icon=@/tmp/menu-test.png;type=image/png" \
	"$API_BASE/v1/admin/menu/$MENU_ID/edit" >/dev/null || true

# Show resulting file size and timestamp on disk
if ls -1 "/var/www/LWB/Menu/$MENU_ID/icon.*" >/dev/null 2>&1; then
	for f in "/var/www/LWB/Menu/$MENU_ID/icon.*"; do
		echo "$(basename "$f"): $(stat -c '%s bytes, %y' "$f" || echo 'stat failed')"
	done
else
	echo "No icon files present under /var/www/LWB/Menu/$MENU_ID"
fi

# Tail recent API logs which include debug statements
journalctl -u lwb-admin-api.service -n 60 --no-pager | sed -n '1,60p'