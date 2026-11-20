#!/bin/sh
# Builds the dashboard into web/dist with the map basemap switched on. Run from anywhere.
set -e
cd "$(dirname "$0")/../web"
npm ci
VITE_MAP_STYLE_URL=/map/style.json npm run build
grep -q '/map/style.json' dist/assets/*.js && echo "ok: dist contains /map/style.json" || { echo "WARNING: basemap address not found in dist"; exit 1; }
