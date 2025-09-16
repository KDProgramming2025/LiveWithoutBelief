Step 1: Create env file at /etc/lwb-server.env (single command)

cat > /etc/lwb-server.env << 'EOF'
PORT=4433
ALTCHA_SECRET=change-me
PGHOST=127.0.0.1
PGPORT=5432
PGUSER=lwb_app
PGPASSWORD=change-me
PGDATABASE=lwb
NODE_ENV=production
LWB_ENV_FILE=/etc/lwb-server.env
EOF

Step 2: Install systemd unit

install -o root -g root -m 0644 /var/www/LWB/server/deploy/lwb-server.service /etc/systemd/system/lwb-server.service

Step 3: Reload systemd

systemctl daemon-reload

Step 4: Enable service

systemctl enable lwb-server

Step 5: Start service

systemctl start lwb-server

Step 6: Check status

systemctl --no-pager status lwb-server
