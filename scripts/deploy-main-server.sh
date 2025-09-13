#!/usr/bin/env bash
set -euo pipefail

# Helper script to build the main Server API on the server using isolated Node and manage systemd/nginx.
# This mirrors Admin deploy but targets the main server (public API consumed by the Android app).

NODE_HOME="/opt/lwb-node/current"
export PATH="${NODE_HOME}/bin:${PATH}"
NODE_BIN="${NODE_HOME}/bin/node"
NPM_BIN="${NODE_HOME}/bin/npm"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRV_DIR="$REPO_ROOT/server"
SRV_DST_DIR="/opt/lwb-server"
SERVER_ENV="/etc/lwb-server.env"
SERVICE_NAME="lwb-server.service"

log() { printf "[DEPLOY:SERVER] %s\n" "$*"; }
run() { log "+ $*"; "$@"; }

log "Pulling latest"
run git -C "$REPO_ROOT" fetch --all --prune
run git -C "$REPO_ROOT" pull --ff-only || true

log "Building Server API"
run "$NPM_BIN" --prefix "$SRV_DIR" ci
run "$NPM_BIN" --prefix "$SRV_DIR" run build

log "Syncing to $SRV_DST_DIR"
mkdir -p "$SRV_DST_DIR"
rsync -a --delete "$SRV_DIR/dist/" "$SRV_DST_DIR/"

log "Writing systemd unit"
cat > "/etc/systemd/system/$SERVICE_NAME" <<SYSTEMD
[Unit]
Description=LWB Main Server API
After=network.target
Wants=network-online.target

[Service]
EnvironmentFile=$SERVER_ENV
Environment=SERVER_API_PORT=4433
Environment=HOST=127.0.0.1
WorkingDirectory=$SRV_DST_DIR
ExecStart=$NODE_BIN $SRV_DST_DIR/index.js
Type=simple
Restart=always
RestartSec=3
User=www-data
Group=www-data
StandardOutput=journal
StandardError=inherit

[Install]
WantedBy=multi-user.target
SYSTEMD

run systemctl daemon-reload
run systemctl enable "$SERVICE_NAME" || true
run systemctl restart "$SERVICE_NAME"

log "Deploy complete"
