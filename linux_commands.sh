export PATH=/opt/lwb-node/current/bin:$PATH
cd /var/www/LWB
git fetch --all
git reset --hard github/main
sudo cp server/deploy/nginx/lwb-admin-proxy.conf /etc/nginx/snippets/lwb-admin-proxy.conf
sudo nginx -t && sudo systemctl reload nginx
cd server
npm install
npm run build
sudo systemctl restart lwb-server
