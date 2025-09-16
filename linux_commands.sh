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
