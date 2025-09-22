systemctl is-active lwb-server && systemctl is-enabled lwb-server

set -euo pipefail
cd /var/www/LWB

echo "Detecting git remote..."
REMOTE=$(git remote | head -n1 || true)
if [ -z "${REMOTE:-}" ]; then
	echo "No git remote configured in /var/www/LWB" >&2
	exit 1
fi

TARGET_BRANCH="feature/reader-ui"
echo "Resetting fetch refspec to wildcard and fetching from remote '$REMOTE'..."
git config --unset-all remote."$REMOTE".fetch || true
git config --add remote."$REMOTE".fetch +refs/heads/*:refs/remotes/"$REMOTE"/*
git fetch "$REMOTE" --prune

echo "Checking if remote branch $TARGET_BRANCH exists..."
if git ls-remote --heads "$REMOTE" "$TARGET_BRANCH" | grep -q "$TARGET_BRANCH"; then
	echo "Switching to $TARGET_BRANCH from $REMOTE..."
	git checkout -B "$TARGET_BRANCH" || true
	git reset --hard "$REMOTE/$TARGET_BRANCH"
else
	echo "Remote branch $TARGET_BRANCH not found on $REMOTE. Fast-forwarding current branch..."
	git pull --ff-only "$REMOTE" "$(git rev-parse --abbrev-ref HEAD)" || true
fi

echo "Installing server deps and building (if needed)..."
cd server
/opt/lwb-node/current/bin/npm ci --no-audit --no-fund
/opt/lwb-node/current/bin/npm run build --silent

echo "Restarting lwb-server service..."
sudo systemctl restart lwb-server
echo "Done."
