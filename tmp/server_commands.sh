#!/usr/bin/env bash
set -euo pipefail

echo "[LWB] Starting Admin deploy script"

# Constants
WEB_DST_DIR=/var/www/LWB/Admin
API_DST_DIR=/opt/lwb-admin-api
NGINX_SNIPPET=/etc/nginx/snippets/lwb-admin-location.conf
SERVER_ENV=/etc/lwb-server.env
NODE_BIN=/opt/lwb-node/current/bin/node

mkdir -p "$WEB_DST_DIR"
mkdir -p "$API_DST_DIR"

if [ -f /tmp/admin-web-dist.zip ]; then
	echo "[LWB] Deploying Admin Web to $WEB_DST_DIR"
	rm -rf /tmp/admin-web-dist
	mkdir -p /tmp/admin-web-dist
	unzip -q -o /tmp/admin-web-dist.zip -d /tmp/admin-web-dist
	rsync -a --delete /tmp/admin-web-dist/ "$WEB_DST_DIR"/
	find "$WEB_DST_DIR" -type d -exec chmod 755 {} +
	find "$WEB_DST_DIR" -type f -exec chmod 644 {} +
fi

if [ -f /tmp/admin-api-dist.zip ]; then
	echo "[LWB] Deploying Admin API to $API_DST_DIR"
	rm -rf /tmp/admin-api-dist
	mkdir -p /tmp/admin-api-dist
	unzip -q -o /tmp/admin-api-dist.zip -d /tmp/admin-api-dist
	# Expecting dist/index.js and optionally assets
	rsync -a --delete /tmp/admin-api-dist/ "$API_DST_DIR"/
	chmod 755 "$API_DST_DIR"
	find "$API_DST_DIR" -type f -name "*.sh" -exec chmod +x {} + || true
fi

echo "[LWB] Writing nginx snippet at $NGINX_SNIPPET"
cat > "$NGINX_SNIPPET" <<'NGINX'
location = /LWB/Admin {
	return 301 /LWB/Admin/;
}

location ^~ /LWB/Admin/api/ {
	proxy_http_version 1.1;
	proxy_set_header Host $host;
	proxy_set_header X-Real-IP $remote_addr;
	proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
	proxy_set_header X-Forwarded-Proto $scheme;
	proxy_pass http://127.0.0.1:5050/;
}

location ^~ /LWB/Admin/ {
	alias /var/www/LWB/Admin/;
	try_files $uri $uri/ /LWB/Admin/index.html;
	add_header Cache-Control "public, max-age=60";
}
NGINX

echo "[LWB] Ensuring nginx server block includes the snippet"
mapfile -t SERVER_BLOCKS < <(grep -Rl "server_name[[:space:]]\+aparat.feezor.net;" /etc/nginx/sites-available || true)
for sb in "${SERVER_BLOCKS[@]}"; do
	if ! grep -q "snippets/lwb-admin-location.conf" "$sb"; then
		sed -i "/server_name[[:space:]]\+aparat.feezor.net;/a \\tinclude $NGINX_SNIPPET;" "$sb"
		echo "[LWB] Inserted include into $sb"
	else
		echo "[LWB] Include already present in $sb"
	fi
done

echo "[LWB] Testing and reloading nginx"
nginx -t
systemctl reload nginx

echo "[LWB] Creating/Updating systemd unit lwb-admin-api.service"
cat > /etc/systemd/system/lwb-admin-api.service <<SYSTEMD
[Unit]
Description=LWB Admin API
After=network.target
Wants=network-online.target

[Service]
EnvironmentFile=$SERVER_ENV
WorkingDirectory=$API_DST_DIR
ExecStart=$NODE_BIN $API_DST_DIR/dist/index.js
Restart=always
RestartSec=3
User=www-data
Group=www-data
StandardOutput=journal
StandardError=inherit

[Install]
WantedBy=multi-user.target
SYSTEMD

echo "[LWB] Reloading systemd and (re)starting lwb-admin-api"
systemctl daemon-reload
systemctl enable lwb-admin-api.service || true
systemctl restart lwb-admin-api.service || true

echo "[LWB] Admin deploy script finished"
