export PATH=/opt/lwb-node/current/bin:$PATH
cd /var/www/LWB
git fetch --all
git reset --hard github/main
cd server
npm install
npm run build
sudo systemctl restart lwb-server
