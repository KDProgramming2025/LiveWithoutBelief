#!/usr/bin/env bash
set -euo pipefail

# Ensure Node toolchain path (systemd may not inherit interactive PATH)
export PATH="/opt/lwb-node/current/bin:${PATH}" || true

# LiveWithoutBelief backend deploy script
# Features:
#  - Safe idempotent deploy (skip if no new commit)
#  - Optional dry-run (--dry-run)
#  - Rollback on failed restart/health check
#  - Skips npm ci if package-lock.json unchanged
#  - Records last & previous commit hashes
#  - Supports ExecReload path (--reload: rebuild + restart only)

SERVICE_NAME="lwb-server"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$APP_DIR/deploy.log"
LAST_FILE="$APP_DIR/.deploy_last_commit"
PREV_FILE="$APP_DIR/.deploy_prev_commit"
LOCK_HASH_FILE="$APP_DIR/.deploy_lock_hash"
BRANCH_DEFAULT="main"

color() { local c="$1"; shift; [[ -t 1 ]] || { echo "$*"; return; }; case "$c" in green) code='\e[32m';; yellow) code='\e[33m';; red) code='\e[31m';; blue) code='\e[34m';; *) code='';; esac; printf "%b%s\e[0m\n" "$code" "$*"; }
ensure_log_path() {
  # If directory not writable, fallback to /tmp
  if [ ! -w "$APP_DIR" ]; then
    LOG_FILE="/tmp/lwb-deploy.log"
  fi
  # If log file not creatable, fallback again
  if ! ( touch "$LOG_FILE" 2>/dev/null ); then
    LOG_FILE="/tmp/lwb-deploy.log"
    touch "$LOG_FILE" 2>/dev/null || true
  fi
}
ensure_log_path

log() { color blue "[deploy] $*" | { if [ -w "$(dirname "$LOG_FILE")" ]; then tee -a "$LOG_FILE"; else cat; fi; }; }
warn() { color yellow "[deploy] $*" | { if [ -w "$(dirname "$LOG_FILE")" ]; then tee -a "$LOG_FILE"; else cat; fi; }; }
err() { color red "[deploy] $*" | { if [ -w "$(dirname "$LOG_FILE")" ]; then tee -a "$LOG_FILE"; else cat; fi; }; }

MODE="normal"
for a in "$@"; do
  case "$a" in
    --dry-run) MODE="dry" ;;
    --reload) MODE="reload" ;;
  esac
done

CURRENT_BRANCH="$(git -C "$APP_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
TARGET_BRANCH="${DEPLOY_BRANCH:-$CURRENT_BRANCH}" # allow override

if [[ "$MODE" != "reload" ]]; then
  log "Deploy mode: $MODE (branch: $TARGET_BRANCH)"
else
  log "Reload mode: rebuild & restart only"
fi

cd "$APP_DIR"

if [[ "$MODE" != "reload" ]]; then
  git fetch --quiet origin "$TARGET_BRANCH" || warn "Fetch failed (continuing with local)"
  LATEST_REMOTE="$(git rev-parse "origin/$TARGET_BRANCH" 2>/dev/null || git rev-parse HEAD)"
  CURRENT_COMMIT="$(git rev-parse HEAD)"
  if [[ "$MODE" == "dry" ]]; then
    log "Current commit: $CURRENT_COMMIT"
    log "Remote latest : $LATEST_REMOTE"
  fi
  if [[ "$CURRENT_COMMIT" == "$LATEST_REMOTE" && "$MODE" == "normal" ]]; then
    log "No new commits; exiting."; exit 0; fi
  if [[ "$MODE" != "dry" && "$CURRENT_COMMIT" != "$LATEST_REMOTE" ]]; then
    log "Pulling new commits..."; git pull --ff-only origin "$TARGET_BRANCH"; CURRENT_COMMIT="$(git rev-parse HEAD)"; fi
fi

CHANGED_TS_FILES="$(git diff --name-only HEAD~1 HEAD 2>/dev/null | grep -E '^server/src/.*\.ts$' || true)"
LOCK_HASH=""; OLD_LOCK_HASH=""; NEED_INSTALL=0
if [[ -f package-lock.json ]]; then
  LOCK_HASH="$(sha256sum package-lock.json | cut -d' ' -f1)"
  [[ -f "$LOCK_HASH_FILE" ]] && OLD_LOCK_HASH="$(cat "$LOCK_HASH_FILE")"
  if [[ "$LOCK_HASH" != "$OLD_LOCK_HASH" ]]; then NEED_INSTALL=1; fi
fi

if [[ "$MODE" == "dry" ]]; then
  log "Dry-run summary:";
  [[ "$MODE" != reload ]] && log "Would deploy commit: $(git rev-parse HEAD)"
  log "package-lock changed: $NEED_INSTALL"
  log "TypeScript change candidates: ${CHANGED_TS_FILES:-<none detected>}"
  exit 0
fi

if [[ "$MODE" != "reload" ]]; then
  [[ -f "$LAST_FILE" ]] && cp "$LAST_FILE" "$PREV_FILE" || true
  git rev-parse HEAD > "$LAST_FILE"
fi

if [[ $NEED_INSTALL -eq 1 ]]; then
  log "Lock hash changed; running npm ci"; npm ci --no-audit --no-fund; echo "$LOCK_HASH" > "$LOCK_HASH_FILE";
else
  log "Skipping npm ci (lock unchanged)";
fi

log "Building (tsc)..."; npx tsc -p tsconfig.json --pretty false

restart_service() {
  if [[ $(id -un) == lwbapp ]]; then
    if command -v sudo >/dev/null 2>&1; then sudo systemctl restart "$SERVICE_NAME"; else systemctl restart "$SERVICE_NAME"; fi
  else
    systemctl restart "$SERVICE_NAME"
  fi
}

log "Restarting service..."; restart_service

PORT_ENV="${PORT:-4433}" # systemd sets PORT=4433
LOCAL_HEALTH="http://127.0.0.1:${PORT_ENV}/health"
PUBLIC_HEALTH="${PUBLIC_HEALTH_URL:-}" # optional external check

attempts=20; ok=0
for i in $(seq 1 $attempts); do
  if curl -fsS "$LOCAL_HEALTH" | grep -q '"ok"'; then ok=1; break; fi
  sleep 1
done

if [[ $ok -ne 1 ]]; then
  err "Local health check failed after $attempts attempts"
  if [[ -f "$PREV_FILE" ]]; then
    warn "Rolling back"; prev=$(cat "$PREV_FILE"); git reset --hard "$prev"; npm ci --no-audit --no-fund || true; npx tsc -p tsconfig.json --pretty false || true; restart_service || true;
  fi
  exit 1
fi
log "Local health OK"

if [[ -n "$PUBLIC_HEALTH" ]]; then
  if curl -fsS "$PUBLIC_HEALTH" | grep -q '"ok"'; then log "Public health OK"; else warn "Public health check failed (continuing)"; fi
fi

log "Deploy complete: $(git rev-parse HEAD)"