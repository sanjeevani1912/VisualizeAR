#!/usr/bin/env bash
# Backs up the compose stack: the PostgreSQL database AND the uploads folder (field images) from the Django volume.
# Writes two timestamped files into server-django/backups/ and keeps the newest N of each (default 14).
#   bash deploy/backup-stack.sh        or        bash deploy/backup-stack.sh 30
# (server-django/scripts/backup_postgres.sh is for the older single-container development database and does not fit this stack:
#  different container name and password file.)  Restore steps: docs/STACK.md.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash must not rewrite /backups and /data
HERE="$(cd "$(dirname "$0")" && pwd)"
BACKUPS="$HERE/../server-django/backups"
KEEP="${1:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUPS"
cd "$HERE"
dc() { docker compose "$@"; }

echo "1. database -> server-django/backups/dhruvar-$STAMP.dump"
dc exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /backups/dhruvar-'"$STAMP"'.dump'
N="$(dc exec -T postgres pg_restore --list "/backups/dhruvar-$STAMP.dump" | grep -c 'TABLE DATA' || true)"
[ "$N" -gt 0 ] || { echo "ERROR: the dump contains no table data"; rm -f "$BACKUPS/dhruvar-$STAMP.dump"; exit 1; }
echo "   dump readable: $N tables with data"

echo "2. images -> server-django/backups/uploads-$STAMP.tar.gz"
dc exec -T django tar -czf - -C /data uploads > "$BACKUPS/uploads-$STAMP.tar.gz"
tar -tzf "$BACKUPS/uploads-$STAMP.tar.gz" > /dev/null
echo "   $(tar -tzf "$BACKUPS/uploads-$STAMP.tar.gz" | grep -vc '/$' || true) image file(s)"

echo "3. keeping the newest $KEEP of each"
for pattern in "dhruvar-*.dump" "uploads-*.tar.gz"; do
  ls -1t "$BACKUPS"/$pattern 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do echo "   removing $(basename "$old")"; rm -f "$old"; done
done
echo "done. Copy server-django/backups/ to ANOTHER disk or machine too."
