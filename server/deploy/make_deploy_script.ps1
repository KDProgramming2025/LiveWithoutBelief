# Writes a single Linux command into linux_commands.sh to deploy the built server to lwb-server and restart the service.
# Usage: Run this script from repo root or server folder after `npm run build`.

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$distPath = Join-Path $repoRoot 'server\dist'
if (!(Test-Path $distPath)) {
  throw "dist not found. Build the server first (npm --prefix server run build)."
}

$remoteScript = @(
  'set -e',
  'install -d -o www-data -g www-data -m 0755 /var/www/LWB/server/dist',
  'rsync -a --delete /var/www/LWB/server/dist/ /var/www/LWB/server/dist.bak/ || true',
  'rsync -a --delete --chown=www-data:www-data /tmp/lwb-dist/ /var/www/LWB/server/dist/',
  'systemctl restart lwb-server',
  'systemctl --no-pager status lwb-server | head -n 25'
) -join " && "

# Prepare temp packaging of dist
$temp = New-Item -ItemType Directory -Force -Path ([System.IO.Path]::GetTempPath() + 'lwb-dist')
Remove-Item -Recurse -Force ($temp.FullName + '\*') -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force "$distPath\*" $temp.FullName

# Write linux_commands.sh with one command that performs copy and restart
$singleCommand = @(
  'rm -rf /tmp/lwb-dist && mkdir -p /tmp/lwb-dist',
  ' && ',
  'cat > /tmp/remote-deploy.sh <<\' + "'EOF" + '\n' + $remoteScript + '\nEOF',
  ' && ',
  'bash /tmp/remote-deploy.sh'
) -join ''

Set-Content -Path (Join-Path $repoRoot 'linux_commands.sh') -Value $singleCommand -NoNewline
Write-Host "Wrote linux_commands.sh. Next: use the Deploy LWB server task to run it on the server." -ForegroundColor Green