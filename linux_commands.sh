#!/usr/bin/env bash
set -euo pipefail

# Build and deploy Admin API and Admin Web, then verify basics

API_PROXY="https://aparat.feezor.net/LWB/Admin/api"
WEB_DIR_REMOTE="/var/www/LWB/Admin/Web"

echo "--- Ensure single 'github' remote and fetch branch ---"
cd /var/www/LWB/LiveWithoutBelief
# Remove any stray 'origin' remote to adhere to policy (single remote named 'github')
git remote remove origin 2>/dev/null || true
# Ensure 'github' remote URL is correct (idempotent)
git remote set-url github https://github.com/KDProgramming2025/LiveWithoutBelief.git 2>/dev/null || true
# Fetch from 'github' only and ensure feature branch exists locally under refs/remotes/github/*
git fetch github --prune
git fetch github feature/LWB-92-admin-ui:refs/remotes/github/feature/LWB-92-admin-ui || true
git reset --hard github/feature/LWB-92-admin-ui
git clean -fd

echo "--- Build Admin API ---"
cd admin/api
/opt/lwb-node/current/bin/npm ci --prefer-offline --no-audit
/opt/lwb-node/current/bin/npm run build

echo "--- Deploy Admin API artifact ---"
rsync -av --delete \
	--include '*/' --include '*.js' --include '*.js.map' --exclude '*' \
	dist/ /opt/lwb-admin-api/
systemctl restart lwb-admin-api
sleep 1
journalctl -u lwb-admin-api -n 10 --no-pager | tail -n 10 || true

echo "--- Build Admin Web ---"
cd ../web
/opt/lwb-node/current/bin/npm ci --prefer-offline --no-audit
/opt/lwb-node/current/bin/npm run build

echo "--- Deploy Admin Web dist ---"
mkdir -p "$WEB_DIR_REMOTE"
rsync -av --delete dist/ "$WEB_DIR_REMOTE"/

echo "--- Verify Admin Web index ---"
curl -sS -I https://aparat.feezor.net/LWB/Admin/Web/ | sed -n '1,20p' || true

echo "--- Verify Admin API session (unauth expected) ---"
curl -sS -k "$API_PROXY/v1/admin/session" | sed -n '1,120p' || true

echo "Done."
