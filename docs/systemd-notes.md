Systemd Integration Notes
=========================

Service: lwb-server

Recommended unit additions (edit on server at /etc/systemd/system/lwb-server.service):

  EnvironmentFile=/etc/lwb-server.env
  ExecReload=/opt/lwb-app/server/deploy.sh --reload

Environment file (/etc/lwb-server.env) sample (not committed to VCS):

  NODE_ENV=production
  GOOGLE_CLIENT_ID=186459205865-2h72dus840f2db75c4fp2vijqdrubchv.apps.googleusercontent.com
  PORT=4433
  PUBLIC_HEALTH_URL=https://aparat.feezor.net/lwb-api/health
  # Future variables:
  # DB_URL=...
  # REDIS_URL=...
  # LOG_LEVEL=info

Example snippet (inside [Service]):

  [Service]
  Type=simple
  User=lwbapp
  Group=lwbapp
  WorkingDirectory=/opt/lwb-app/server
  ExecStart=/opt/lwb-node/current/bin/node dist/index.js
  ExecReload=/opt/lwb-app/server/deploy.sh --reload
  EnvironmentFile=/etc/lwb-server.env  # replaces inline Environment= lines
  Restart=on-failure
  RestartSec=5
  NoNewPrivileges=true
  PrivateTmp=true
  ProtectSystem=strict
  ProtectHome=true

After editing the unit on server:
  sudo systemctl daemon-reload
  sudo systemctl restart lwb-server.service

To trigger reload logic (rebuild & restart without git pull):
  sudo systemctl reload lwb-server.service
