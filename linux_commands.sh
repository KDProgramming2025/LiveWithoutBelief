#!/usr/bin/env bash
/usr/bin/git -C /var/www/LWB/server pull --ff-only
sudo systemctl restart lwb-server
