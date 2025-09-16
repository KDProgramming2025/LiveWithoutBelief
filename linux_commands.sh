#!/usr/bin/env bash
set -euo pipefail

# Single-step: deploy Admin API, then E2E login + icon upload (proxy and direct) + HTTP/disk verification + diagnostics

API_PROXY="https://aparat.feezor.net/LWB/Admin/api"
API_LOCAL="http://127.0.0.1:5050"
ICON_FILE="/tmp/menu-test.png"

# Deploy latest code (Admin API only)
cd /var/www/LWB/LiveWithoutBelief
git fetch --all --prune
git reset --hard origin/feature/LWB-92-admin-ui
git clean -fd
cd admin/api
/opt/lwb-node/current/bin/npm ci --prefer-offline --no-audit
/opt/lwb-node/current/bin/npm run build
# Sync only built JS + sourcemaps to avoid wiping runtime data directory
rsync -av --delete \
	--include '*/' --include '*.js' --include '*.js.map' --exclude '*' \
	dist/ /opt/lwb-admin-api/
systemctl restart lwb-admin-api
sleep 1
journalctl -u lwb-admin-api -n 20 --no-pager | tail -n 20

# Create a tiny but non-empty PNG for testing (1x1 transparent, ~71 bytes)
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\x0cIDAT\x08\xd7c``\xf8\xff\xff?\x00\x05\xfe\x02\xfeA\x90\x80\xf0\x00\x00\x00\x00IEND\xaeB`\x82' > "$ICON_FILE"

# Ensure ADMIN creds are available from env file if present
if [ -f /etc/lwb-server.env ]; then set -a; . /etc/lwb-server.env; set +a; fi
: "${ADMIN_USER:=${ADMIN_USER:-${ADMIN_PANEL_USERNAME:-}}}"
: "${ADMIN_PASS:=${ADMIN_PASS:-${ADMIN_PANEL_PASSWORD:-}}}"

if [ -z "${ADMIN_USER}" ] || [ -z "${ADMIN_PASS}" ]; then
	echo "Missing ADMIN_USER/ADMIN_PASS in env; export before running." >&2
	exit 1
fi

# 1) Login via proxy and capture cookie
COOKIE_JAR="/tmp/lwb_admin_cookies.txt"
rm -f "$COOKIE_JAR"

LOGIN_JSON=$(mktemp)
curl -sS -k -c "$COOKIE_JAR" -H "Content-Type: application/json" \
	-d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" \
	"$API_PROXY/v1/admin/login" | tee "$LOGIN_JSON" | cat
echo

# Extract auth token from cookie jar (for direct localhost call)
TOKEN=$(awk '$0 !~ /^#/ && $6=="lwb_admin" {print $7}' "$COOKIE_JAR" | tail -n1)
echo "--- cookie jar (filtered) ---"
grep -v '^#' "$COOKIE_JAR" || true
echo "(parsed token len=${#TOKEN})"

# 2) Ensure at least one menu item exists; create one if empty
MENU_JSON="/tmp/menu_list.json"
curl -sS -k -b "$COOKIE_JAR" "$API_PROXY/v1/admin/menu" | tee "$MENU_JSON" >/dev/null
MENU_ID=$(sed -n 's/.*"id":"\([^"\]+\)".*/\1/p' "$MENU_JSON" | head -n1)
if [ -z "$MENU_ID" ]; then
	echo "No menu items via API; creating one..."
	ADD_JSON="/tmp/menu_add.json"
	curl -sS -k -b "$COOKIE_JAR" \
		-F "title=Home" -F "label=home" -F "order=1" -F "icon=@$ICON_FILE;type=image/png" \
		"$API_PROXY/v1/admin/menu" | tee "$ADD_JSON" | cat
	echo
	MENU_ID=$(sed -n 's/.*"id":"\([^"\]+\)".*/\1/p' "$ADD_JSON" | head -n1)
fi
if [ -z "$MENU_ID" ]; then
	echo "Still no MENU_ID; trying filesystem fallback" >&2
	MENU_ID=$(ls -1 /var/www/LWB/Menu 2>/dev/null | head -n1 || true)
fi
if [ -z "$MENU_ID" ]; then
	echo "No MENU_ID available" >&2
	exit 1
fi

# 2b) Reconcile metadata from existing icon folders (if metadata was lost)
FOLDERS=$(ls -1 /var/www/LWB/Menu 2>/dev/null | grep -vE '^(Admin|Web)$' || true)
EXISTING_IDS=$(sed -n 's/.*"id":"\([^"\]+\)".*/\1/p' "$MENU_JSON" || true)
COUNT=$(echo "$EXISTING_IDS" | grep -c . || true)
if [ -z "$COUNT" ] || [ "$COUNT" -lt 1 ]; then COUNT=0; fi
NEXT_ORDER=$(( COUNT + 1 ))
for F in $FOLDERS; do
  echo "$EXISTING_IDS" | grep -qx "$F" && continue
  echo "Recovering menu item from folder: $F"
  ICON_PATH=$(ls -1 "/var/www/LWB/Menu/$F"/icon.* 2>/dev/null | head -n1 || true)
  TITLE=$(echo "$F" | sed -E 's/(^|[-_ ])([a-z])/\U\2/g')
  if [ -n "$ICON_PATH" ]; then
    curl -sS -k -b "$COOKIE_JAR" \
      -F "title=$TITLE" -F "label=$F" -F "order=$NEXT_ORDER" -F "icon=@$ICON_PATH" \
      "$API_PROXY/v1/admin/menu" | cat
  else
    curl -sS -k -b "$COOKIE_JAR" \
      -F "title=$TITLE" -F "label=$F" -F "order=$NEXT_ORDER" \
      "$API_PROXY/v1/admin/menu" | cat
  fi
  echo
  NEXT_ORDER=$(( NEXT_ORDER + 1 ))
done
# Refresh MENU_JSON after reconciliation
curl -sS -k -b "$COOKIE_JAR" "$API_PROXY/v1/admin/menu" | tee "$MENU_JSON" >/dev/null

echo "Using MENU_ID=$MENU_ID"

# 3) Upload icon through proxy (71-byte PNG)
curl -sS -k -b "$COOKIE_JAR" -F "icon=@$ICON_FILE;type=image/png" "$API_PROXY/v1/admin/menu/$MENU_ID/edit" | cat
echo

## 4) Upload a larger 256-byte payload via proxy AND (if possible) direct local
dd if=/dev/zero of=/tmp/menu-test-256.bin bs=1 count=256 status=none
echo "-- proxy edit (256B) --"
curl -sS -k -b "$COOKIE_JAR" -F "icon=@/tmp/menu-test-256.bin;filename=icon.png;type=image/png" "$API_PROXY/v1/admin/menu/$MENU_ID/edit" | cat
echo
if [ -n "$TOKEN" ]; then
	echo "-- direct edit (256B) --"
	curl -sS -H "Cookie: lwb_admin=$TOKEN" -F "icon=@/tmp/menu-test-256.bin;filename=icon.png;type=image/png" "$API_LOCAL/v1/admin/menu/$MENU_ID/edit" | cat
	echo
else
	echo "No token extracted; skipping direct local upload"
fi

# 5) Verify over HTTP (HEAD) to canonical public URL
ICON_URL="https://aparat.feezor.net/LWB/Admin/Menu/$MENU_ID/icon.png"
echo "--- HTTP HEAD $ICON_URL (after uploads) ---"
curl -sSI "$ICON_URL" | sed -n '1,20p'
echo

# 6) Print on-disk files and sizes AFTER final edit
echo "--- Disk files for $MENU_ID ---"
ls -la "/var/www/LWB/Menu/$MENU_ID" || true
echo "--- stat for icon.* ---"
stat "/var/www/LWB/Menu/$MENU_ID"/icon.* 2>/dev/null || true

# 7) Show current metadata and recent logs for multipart/menu debug
echo "--- /opt/lwb-admin-api/data/menu.json ---"
sed -n '1,200p' /opt/lwb-admin-api/data/menu.json 2>/dev/null || true
echo "--- recent logs ---"
journalctl -u lwb-admin-api -n 200 --no-pager | grep -E '\[DEBUG\]|multipart|menu|request completed|incoming request' || true