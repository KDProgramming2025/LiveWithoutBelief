Systemd Integration Notes
=========================

Service: lwb-server

Recommended unit additions (edit on server at /etc/systemd/system/lwb-server.service):

  EnvironmentFile=/etc/lwb-server.env
  ExecReload=/opt/lwb-app/server/deploy.sh --reload

Example snippet (inside [Service]):

  [Service]
  Type=simple
  User=lwbapp
  Group=lwbapp
  WorkingDirectory=/opt/lwb-app/server
  ExecStart=/opt/lwb-node/current/bin/node dist/index.js
  ExecReload=/opt/lwb-app/server/deploy.sh --reload
  EnvironmentFile=/etc/lwb-server.env
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
