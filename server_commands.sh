set -e

META="/opt/lwb-admin-api/data/menu.json"
MENU_DIR="/var/www/LWB/Menu"
PUBLIC_PREFIX="https://aparat.feezor.net/LWB/Menu"

# Ensure menu dir exists with correct ownership/permissions
mkdir -p "$MENU_DIR"
chown -R www-data:www-data "$MENU_DIR"
chmod -R 775 "$MENU_DIR"

# Show a snippet of meta for debugging
if [ -f "$META" ]; then
  echo "[META HEAD]"; head -c 400 "$META"; echo
else
  echo "[META] Missing $META" >&2
fi

# Extract IDs using python3 for robustness
IDS_FILE="/tmp/menu_ids.txt"
python3 - "$META" > "$IDS_FILE" <<'PY'
import json, sys
path = sys.argv[1]
try:
    with open(path) as f:
        data = json.load(f)
    if isinstance(data, list):
        for it in data:
            if isinstance(it, dict) and 'id' in it:
                print(it['id'])
except Exception as e:
    pass
PY

COUNT=$(wc -l < "$IDS_FILE" 2>/dev/null || echo 0)
echo "[IDS] Count: $COUNT"

# 1x1 PNG placeholder
PNG_B64="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAOoeBY0AAAAASUVORK5CYII="

if [ -s "$IDS_FILE" ]; then
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    d="$MENU_DIR/$id"
    icon=$(ls "$d"/icon.* 2>/dev/null | head -n1 || true)
    if [ -n "$icon" ]; then
      echo "[SKIP] $id already has icon: $(basename "$icon")"
      continue
    fi
    mkdir -p "$d"
    echo "$PNG_B64" | base64 -d > "$d/icon.png"
    chown -R www-data:www-data "$d"
    chmod 664 "$d/icon.png"
    echo "[ADD] $id -> $PUBLIC_PREFIX/$id/icon.png"
  done < "$IDS_FILE"
else
  echo "[INFO] No IDs to backfill."
fi

# Show resulting files
ls -la "$MENU_DIR"/*/icon.* 2>/dev/null || echo "[INFO] No icons present after backfill."

# Empty the ephemeral script as per workflow guidelines
> "$0"