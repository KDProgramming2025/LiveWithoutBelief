#!/usr/bin/env bash
set -e
cd /var/www/LWB
sudo -u www-data git fetch --all
sudo -u www-data git checkout feature/LWB-92-admin-ui
sudo -u www-data git pull --ff-only github feature/LWB-92-admin-ui
echo "Deployed latest admin web assets."

