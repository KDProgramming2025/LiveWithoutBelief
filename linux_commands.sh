#!/usr/bin/env bash
sudo sed -i 's/^ALTCHA_MAXNUMBER=.*/ALTCHA_MAXNUMBER=20000/' /etc/lwb-server.env || echo 'ALTCHA_MAXNUMBER=20000' | sudo tee -a /etc/lwb-server.env > /dev/null
