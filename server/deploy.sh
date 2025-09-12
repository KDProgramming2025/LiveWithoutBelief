#!/usr/bin/env bash
set -euo pipefail

# LWB Admin Deploy Script (server-side)
# - Builds Admin API and Admin Web on the server using isolated Node toolchain
# - Syncs artifacts to runtime locations
# - Manages systemd service for Admin API
# - Configures nginx locations for /LWB/Admin and /LWB/Admin/api
# - Performs health checks

NODE_HOME="/opt/lwb-node/current"
export PATH="${NODE_HOME}/bin:${PATH}"
NODE_BIN="${NODE_HOME}/bin/node"
NPM_BIN="${NODE_HOME}/bin/npm"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_DIR="$REPO_ROOT/admin/api"
WEB_DIR="$REPO_ROOT/admin/web"

API_DST_DIR="/opt/lwb-admin-api"
WEB_DST_DIR="/var/www/LWB/Admin"
SERVER_ENV="/etc/lwb-server.env"
NGINX_SNIPPET="/etc/nginx/snippets/lwb-admin-location.conf"
DOMAIN_CONF_DIR="/etc/nginx/sites-available"

ADMIN_API_SERVICE="lwb-admin-api.service"

log() { printf "[DEPLOY] %s\n" "$*"; }
run() { log "+ $*"; "$@"; }

ensure_dir() { mkdir -p "$1"; }

# Optional: DEPLOY_BRANCH environment variable to switch branches
if [[ -n "${DEPLOY_BRANCH:-}" ]]; then
	log "Switching to branch: $DEPLOY_BRANCH"
	run git -C "$REPO_ROOT" fetch --all --prune
	run git -C "$REPO_ROOT" checkout "$DEPLOY_BRANCH"
	run git -C "$REPO_ROOT" pull --ff-only
else
	log "Pulling latest on current branch"
	run git -C "$REPO_ROOT" fetch --all --prune
	run git -C "$REPO_ROOT" pull --ff-only || true
fi

# Build Admin API
log "Building Admin API"
run "$NPM_BIN" --prefix "$API_DIR" ci
run "$NPM_BIN" --prefix "$API_DIR" run build

# Deploy Admin API bundle
log "Syncing Admin API runtime to $API_DST_DIR"
ensure_dir "$API_DST_DIR"
run rsync -a --delete "$API_DIR/dist/" "$API_DST_DIR/"

# Ensure writable data directory for runtime (owned by service user)
ensure_dir "$API_DST_DIR/data/articles"
run chown -R www-data:www-data "$API_DST_DIR/data"
run chmod -R 775 "$API_DST_DIR/data"

# Ensure public articles root exists and is writable (for converted content)
ensure_dir "/var/www/LWB/Articles"
run chown -R www-data:www-data "/var/www/LWB/Articles"
run chmod -R 775 "/var/www/LWB/Articles"

# Systemd unit for Admin API
log "Writing systemd unit /etc/systemd/system/$ADMIN_API_SERVICE"
cat > "/etc/systemd/system/$ADMIN_API_SERVICE" <<SYSTEMD
[Unit]
Description=LWB Admin API
After=network.target
Wants=network-online.target

[Service]
EnvironmentFile=$SERVER_ENV
Environment=ADMIN_API_PORT=5050
Environment=ADMIN_API_HOST=127.0.0.1
WorkingDirectory=$API_DST_DIR
ExecStart=$NODE_BIN $API_DST_DIR/server.js
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
run systemctl enable "$ADMIN_API_SERVICE" || true
run systemctl restart "$ADMIN_API_SERVICE"

# Wait for 5050
log "Waiting for Admin API on 127.0.0.1:5050"
for i in $(seq 1 40); do
	if ss -tulpn 2>/dev/null | grep -q ":5050"; then break; fi
	if netstat -tulpn 2>/dev/null | grep -q ":5050"; then break; fi
	sleep 0.5
done

# Probe API
log "Probing Admin API session endpoint"
set +e
API_PROBE=$(curl -sS -m 5 http://127.0.0.1:5050/v1/admin/session || true)
set -e
log "API /v1/admin/session: ${API_PROBE:-<no response>}"

# Build Admin Web
log "Building Admin Web"
run "$NPM_BIN" --prefix "$WEB_DIR" ci
run "$NPM_BIN" --prefix "$WEB_DIR" run build

log "Syncing Admin Web to $WEB_DST_DIR"
ensure_dir "$WEB_DST_DIR"
run rsync -a --delete "$WEB_DIR/dist/" "$WEB_DST_DIR/"
run find "$WEB_DST_DIR" -type d -exec chmod 755 {} +
run find "$WEB_DST_DIR" -type f -exec chmod 644 {} +

# Nginx snippet for Admin
log "Writing nginx snippet at $NGINX_SNIPPET"
cat > "$NGINX_SNIPPET" <<'NGINX'
location = /LWB/Admin {
		return 301 /LWB/Admin/;
}

location ^~ /LWB/Admin/api/ {
		# Increase limits/timeouts for large uploads
		client_max_body_size 256m;
		proxy_http_version 1.1;
		proxy_set_header Host $host;
		proxy_set_header X-Real-IP $remote_addr;
		proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
		proxy_set_header X-Forwarded-Proto $scheme;
		# Timeouts to avoid HTTP2 ping failures during long uploads
		proxy_read_timeout 300s;
		proxy_send_timeout 300s;
		send_timeout 300s;
		# Buffering tweaks: allow streaming to upstream
		proxy_request_buffering off;
		proxy_buffering off;
		proxy_pass http://127.0.0.1:5050/;
}

location ^~ /LWB/Admin/ {
		alias /var/www/LWB/Admin/;
		try_files $uri $uri/ /LWB/Admin/index.html;
		add_header Cache-Control "public, max-age=60";
}

# Serve public articles as static content under /LWB/Articles/<slug>
location ^~ /LWB/Articles/ {
		alias /var/www/LWB/Articles/;
		index index.html;
		# Resolve to file or directory; 'index' will serve index.html for directories
		try_files $uri $uri/ =404;
		add_header Cache-Control "public, max-age=60";
}
NGINX

log "Ensuring nginx server block includes the snippet"
mapfile -t SERVER_BLOCKS < <(grep -Rl "server_name[[:space:]]\+aparat.feezor.net;" "$DOMAIN_CONF_DIR" || true)
for sb in "${SERVER_BLOCKS[@]}"; do
	if ! grep -q "snippets/lwb-admin-location.conf" "$sb"; then
		sed -i "/server_name[[:space:]]\+aparat.feezor.net;/a \	include $NGINX_SNIPPET;" "$sb"
		log "Inserted include into $sb"
	else
		log "Include already present in $sb"
	fi
done

log "Testing and reloading nginx"
run nginx -t
run systemctl reload nginx

# Proxy probe
set +e
PROXY_PROBE=$(curl -sS -m 8 https://aparat.feezor.net/LWB/Admin/api/v1/admin/session || true)
set -e
log "Proxy /LWB/Admin/api/v1/admin/session: ${PROXY_PROBE:-<no response>}"

log "Deploy complete"
