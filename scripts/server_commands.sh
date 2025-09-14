#!/usr/bin/env bash
set -euo pipefail

# LWB remote helper
# - Optionally merges ADMIN_PANEL_* from /tmp/lwb_local.env (Windows CRLF tolerant)
# - Generates ADMIN_PANEL_COOKIE_SECRET if missing
# - Restarts lwb-admin-api
# - Runs cookie-based smokes: login → session → users summary → users search

ADMIN_ENV=/etc/lwb-server.env
LOCAL_ENV_TMP=/tmp/lwb_local.env
COOKIE_JAR=/tmp/lwb_admin_cookie.jar
ADMIN_API_PUBLIC=https://aparat.feezor.net/v1/admin
ADMIN_API_INTERNAL=http://127.0.0.1:5050/v1/admin

log() { echo "[LWB] $*"; }
hr() { printf '%*s
' "${COLUMNS:-80}" '' | tr ' ' '-'; }

# 1) Merge ADMIN_PANEL_* from /tmp/lwb_local.env if present
if [[ -f "$LOCAL_ENV_TMP" ]]; then
  log "Merging admin creds from $LOCAL_ENV_TMP into $ADMIN_ENV"
  # Normalize CRLF → LF safely
  sed -i 's/\r$//' "$LOCAL_ENV_TMP" || true

  # Extract keys we care about
  ADMIN_USER=$(grep -E '^ADMIN_PANEL_USERNAME=' "$LOCAL_ENV_TMP" | sed 's/^ADMIN_PANEL_USERNAME=//') || true
  ADMIN_PASS=$(grep -E '^ADMIN_PANEL_PASSWORD=' "$LOCAL_ENV_TMP" | sed 's/^ADMIN_PANEL_PASSWORD=//') || true

  # Create ADMIN_ENV if missing
  sudo touch "$ADMIN_ENV"
  sudo chmod 0640 "$ADMIN_ENV"

  # Remove existing lines to avoid duplicates
  sudo sed -i '/^ADMIN_PANEL_USERNAME=/d' "$ADMIN_ENV"
  sudo sed -i '/^ADMIN_PANEL_PASSWORD=/d' "$ADMIN_ENV"

  if [[ -n "${ADMIN_USER:-}" ]]; then
    echo "ADMIN_PANEL_USERNAME=${ADMIN_USER}" | sudo tee -a "$ADMIN_ENV" >/dev/null
  fi
  if [[ -n "${ADMIN_PASS:-}" ]]; then
    echo "ADMIN_PANEL_PASSWORD=${ADMIN_PASS}" | sudo tee -a "$ADMIN_ENV" >/dev/null
  fi
fi

# 2) Ensure ADMIN_PANEL_COOKIE_SECRET exists (48 random hex chars)
if ! grep -q '^ADMIN_PANEL_COOKIE_SECRET=' "$ADMIN_ENV"; then
  log "Generating ADMIN_PANEL_COOKIE_SECRET"
  SECRET=$(openssl rand -hex 24)
  echo "ADMIN_PANEL_COOKIE_SECRET=${SECRET}" | sudo tee -a "$ADMIN_ENV" >/dev/null
fi

# 3) Restart admin API to pick up env changes
log "Restarting lwb-admin-api to pick up admin env"
sudo systemctl restart lwb-admin-api
sleep 1

# 4) Inspect status and sanitized env
log "Inspecting Admin API service and environment"
log "systemctl status lwb-admin-api --no-pager"
systemctl status lwb-admin-api --no-pager | sed 's/\x1B\[[0-9;]*[A-Za-z]//g' || true
log "systemctl cat lwb-admin-api (unit + drop-ins)"
systemctl cat lwb-admin-api || true
log "journalctl -u lwb-admin-api -n 80 --no-pager"
journalctl -u lwb-admin-api -n 80 --no-pager | sed 's/\x1B\[[0-9;]*[A-Za-z]//g' || true

log "$ADMIN_ENV (sanitized)"
awk 'BEGIN{FS="="; OFS="="} \
  /^ADMIN_PANEL_PASSWORD=/{$2="***REDACTED***"} \
  /^DATABASE_URL=/{print "DATABASE_URL","***REDACTED***";next} \
  /^ADMIN_PANEL_COOKIE_SECRET=/{print "ADMIN_PANEL_COOKIE_SECRET","***REDACTED***";next} \
  {print $0}' "$ADMIN_ENV" | sed 's/\r$//' || true

# 5) Cookie-based smokes
log "Probing Admin API cookie login/session/search"
rm -f "$COOKIE_JAR"

# Read creds from env
source "$ADMIN_ENV"
if [[ -z "${ADMIN_PANEL_USERNAME:-}" || -z "${ADMIN_PANEL_PASSWORD:-}" ]]; then
  log "Admin creds missing; skipping cookie flow"
  exit 0
fi

API_BASE="$ADMIN_API_PUBLIC"

# JSON-escape username/password minimally for curl (-d)
LOGIN_PAYLOAD=$(jq -nc --arg u "$ADMIN_PANEL_USERNAME" --arg p "$ADMIN_PANEL_PASSWORD" '{username:$u,password:$p}')

# Try public login first; fallback to internal if blocked (e.g., 405 by nginx)
HTTP_CODE=$(curl -sS -o /tmp/admin_login.out -w "%{http_code}" -c "$COOKIE_JAR" -X POST \
  -H "Content-Type: application/json" \
  -d "$LOGIN_PAYLOAD" \
  "$API_BASE/login")
if [[ "$HTTP_CODE" != "200" ]]; then
  log "Public login failed: HTTP $HTTP_CODE, retrying internal"
  API_BASE="$ADMIN_API_INTERNAL"
  HTTP_CODE=$(curl -sS -o /tmp/admin_login.out -w "%{http_code}" -c "$COOKIE_JAR" -X POST \
    -H "Content-Type: application/json" \
    -d "$LOGIN_PAYLOAD" \
    "$API_BASE/login")
fi
if [[ "$HTTP_CODE" != "200" ]]; then
  log "Login failed (final): HTTP $HTTP_CODE"; cat /tmp/admin_login.out; exit 1
fi
log "Login OK via $API_BASE"

# Session check
HTTP_CODE=$(curl -sS -o /tmp/admin_session.out -w "%{http_code}" -b "$COOKIE_JAR" "$API_BASE/session")
if [[ "$HTTP_CODE" != "200" ]]; then
  log "Session check failed: HTTP $HTTP_CODE"; cat /tmp/admin_session.out; exit 1
fi
log "Session OK"

# Users summary
HTTP_CODE=$(curl -sS -o /tmp/admin_users_summary.out -w "%{http_code}" -b "$COOKIE_JAR" "$API_BASE/users/summary")
log "users/summary HTTP $HTTP_CODE"; cat /tmp/admin_users_summary.out || true

# Optional targeted email search (from env SEARCH_EMAIL)
if [[ -n "${SEARCH_EMAIL:-}" ]]; then
  Q=$(python3 - <<'PY'
import urllib.parse, os
q=os.environ.get('SEARCH_EMAIL','')
print(urllib.parse.quote(q))
PY
)
  URL="$API_BASE/users/search?q=$Q"
  HTTP_CODE=$(curl -sS -o /tmp/admin_users_search.out -w "%{http_code}" -b "$COOKIE_JAR" "$URL")
  log "users/search HTTP $HTTP_CODE for q=$Q"; cat /tmp/admin_users_search.out || true
fi

log "Done."