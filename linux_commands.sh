cd /var/www/LWB \
&& sudo -u www-data git fetch --all \
&& sudo -u www-data git checkout feature/LWB-92-admin-ui \
&& sudo -u www-data git reset --hard github/feature/LWB-92-admin-ui \
&& cd /var/www/LWB/server \
&& sudo -u www-data /opt/lwb-node/current/bin/npm ci \
&& sudo -u www-data /opt/lwb-node/current/bin/npm run build \
&& sudo systemctl restart lwb-server \
&& echo "Deployed server and admin web assets."