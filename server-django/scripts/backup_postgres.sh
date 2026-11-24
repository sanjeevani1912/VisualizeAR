#!/usr/bin/env bash
# Backs up BOTH things that hold DhruvAR data: the PostgreSQL database and the uploads folder (field images).
# Writes two timestamped files into server-django/backups/ and keeps only the newest N of each (default 14).
#
#   bash scripts/backup_postgres.sh            # keep the newest 14
#   bash scripts/backup_postgres.sh 30         # keep the newest 30
#   DATA_DIR=/path/to/data bash scripts/backup_postgres.sh     # where uploads/ lives (default: server-django/data)
#
# Needs the container from compose.postgres.yml to be running. Exits with an error (and keeps the old backups) if anything fails.
# Restore: see docs/POSTGRESQL-OPERATIONS.md section 5. To run it every night see section 4 there (not set up automatically).
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash on Windows must not rewrite the /backups path given to docker

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
KEEP="${1:-14}"
DATA_DIR="${DATA_DIR:-$ROOT/data}"
BACKUPS="$ROOT/backups"
set -a; . "$ROOT/.env.postgres"; set +a
DB="${POSTGRES_DB:-dhruvar}"; USER="${POSTGRES_USER:-dhruvar}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUPS"

echo "1. database -> backups/dhruvar-$STAMP.dump"
docker exec dhruvar-postgres pg_dump -U "$USER" -d "$DB" -Fc -f "/backups/dhruvar-$STAMP.dump"
ENTRIES="$(docker exec dhruvar-postgres pg_restore --list "/backups/dhruvar-$STAMP.dump" | grep -c 'TABLE DATA' || true)"
[ "$ENTRIES" -gt 0 ] || { echo "ERROR: the dump contains no table data"; rm -f "$BACKUPS/dhruvar-$STAMP.dump"; exit 1; }
echo "   dump readable: $ENTRIES tables with data"

echo "2. images -> backups/uploads-$STAMP.tar.gz"
if [ -d "$DATA_DIR/uploads" ]; then
  tar -czf "$BACKUPS/uploads-$STAMP.tar.gz" -C "$DATA_DIR" uploads
  tar -tzf "$BACKUPS/uploads-$STAMP.tar.gz" >/dev/null     # fails if the archive is damaged
  echo "   $(tar -tzf "$BACKUPS/uploads-$STAMP.tar.gz" | grep -vc '/$' || true) image file(s)"
else
  echo "   no uploads folder at $DATA_DIR/uploads (nothing to copy)"
fi

echo "3. keeping the newest $KEEP of each"
for pattern in "dhruvar-*.dump" "uploads-*.tar.gz"; do
  ls -1t "$BACKUPS"/$pattern 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do echo "   removing $(basename "$old")"; rm -f "$old"; done
done
echo "done. Copy the backups/ folder to ANOTHER disk or machine too: a backup on the same disk does not survive the disk."
