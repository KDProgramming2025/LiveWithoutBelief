#!/usr/bin/env bash
cd /var/www/LWB && sudo -u www-data git fetch --all && sudo -u www-data git checkout feature/LWB-92-admin-ui && sudo -u www-data git --no-pager pull --ff-only

