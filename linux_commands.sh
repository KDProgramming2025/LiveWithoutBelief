#!/usr/bin/env bash
grep -R "location \^~ /v1/admin/" /etc/nginx 2>/dev/null || true

