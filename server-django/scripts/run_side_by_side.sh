#!/usr/bin/env bash
# Throwaway side-by-side run: Node on 4100, Django on 4101, each with its own temporary data folder.
# Usage (Git Bash, from anywhere):  bash server-django/scripts/run_side_by_side.sh [--raw] [--show]
# Needs the Node server's node_modules installed (npm install in DhruvAR/server) and the Django .venv.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
SCRATCH="$(mktemp -d)"   # logs and the two temporary data folders live here and are deleted at the end
NODE_DIR="C:/Users/itcell/AndroidStudioProjects/DhruvAR/server"
DJ_DIR="C:/Users/itcell/AndroidStudioProjects/DhruvAR-django/server-django"
TMP_NODE="$SCRATCH/tmp-node-data"
TMP_DJ="$SCRATCH/tmp-django-data"
mkdir -p "$TMP_NODE" "$TMP_DJ"
export COMPARE_PASSWORD="Cmp-$(python -c 'import secrets;print(secrets.token_hex(6))')-Aa1"

# Optional: run Django on a throwaway PostgreSQL database (needs `docker compose -f compose.postgres.yml up -d` and .env.postgres).
#   USE_POSTGRES=1 bash server-django/scripts/<this script>
PGDB=""
if [ "${USE_POSTGRES:-}" = "1" ]; then
  set -a; . "$DJ_DIR/.env.postgres"; set +a
  PGDB="cmp_$(python -c 'import secrets;print(secrets.token_hex(4))')"
  docker exec dhruvar-postgres psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE $PGDB" >/dev/null || { echo "cannot create the throwaway PostgreSQL database"; exit 2; }
  export DB_ENGINE=postgres DB_NAME="$PGDB" DB_USER="$POSTGRES_USER" DB_PASSWORD="$POSTGRES_PASSWORD" DB_PORT="$POSTGRES_PORT"
  echo "Django will use PostgreSQL (throwaway database $PGDB)"
fi

cleanup() {
  echo "--- stopping servers"
  [ -n "${NODE_PID:-}" ] && taskkill //PID "$NODE_PID" //T //F >/dev/null 2>&1
  [ -n "${DJ_PID:-}" ] && taskkill //PID "$DJ_PID" //T //F >/dev/null 2>&1
  sleep 1
  for port in 4100 4101; do
    pid=$(netstat -ano | grep ":$port " | grep LISTENING | awk '{print $5}' | head -1)
    [ -n "$pid" ] && taskkill //PID "$pid" //T //F >/dev/null 2>&1
  done
  sleep 1
  [ -n "$PGDB" ] && docker exec dhruvar-postgres psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS $PGDB WITH (FORCE)" >/dev/null 2>&1 && echo "--- throwaway PostgreSQL database dropped"
  rm -rf "$SCRATCH"
  echo "--- temp folders removed: $( [ -e "$SCRATCH" ] && echo NO || echo yes )"
  echo "--- ports still listening: $(netstat -ano | grep -E ':(4100|4101) ' | grep -c LISTENING)"
}
trap cleanup EXIT

cd "$NODE_DIR"
DATA_DIR="$TMP_NODE" node_modules/.bin/tsx "$HERE/seed_node_users.mts" || exit 2
(DATA_DIR="$TMP_NODE" PORT=4100 node_modules/.bin/tsx src/index.ts > "$SCRATCH/node.log" 2>&1) &
NODE_PID=$!

cd "$DJ_DIR"
export DATA_DIR="$TMP_DJ"
.venv/Scripts/python manage.py migrate > "$SCRATCH/django-migrate.log" 2>&1 || { echo migrate failed; exit 2; }
.venv/Scripts/python manage.py shell -c "
import os
from api.models import User
for u,n,r,role in [('pat','Pat Planner','Major','planner'),('pia','Pia Planner','Captain','planner'),('rex','Rex Reviewer','Colonel','reviewer'),('rhea','Rhea Reviewer','Colonel','reviewer'),('ada','Ada Admin','Lt Col','admin'),('aud','Aud Auditor','Major','auditor')]:
    User.objects.create_user(u, os.environ['COMPARE_PASSWORD'], name=n, rank=r, role=role)
print('django users', User.objects.count())
" || exit 2
(ALLOWED_HOSTS=127.0.0.1,localhost .venv/Scripts/python manage.py runserver 4101 --noreload > "$SCRATCH/django.log" 2>&1) &
DJ_PID=$!

for i in $(seq 1 40); do
  a=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:4100/api/health)
  b=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:4101/api/health)
  [ "$a" = "200" ] && [ "$b" = "200" ] && break
  sleep 1
done
echo "health: node=$a django=$b"
[ "$a" = "200" ] && [ "$b" = "200" ] || { echo "servers did not start"; tail -5 "$SCRATCH/node.log" "$SCRATCH/django.log"; exit 3; }

.venv/Scripts/python scripts/compare_servers.py "$@"
echo "compare exit code: $?"
