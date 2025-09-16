#!/usr/bin/env bash
# Install systemd unit for the server (single command)
sudo install -o root -g root -m 0644 /var/www/LWB/server/deploy/lwb-server.service /etc/systemd/system/lwb-server.service