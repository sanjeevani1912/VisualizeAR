#!/usr/bin/env bash
# Seeds a throwaway Node server (port 4100) and a throwaway Django server (port 4101), compares the seeded data,
# then runs the SAME end-to-end test file (scripts/api-test.ts) against each.
# Usage (Git Bash):  bash server-django/scripts/run_conformance.sh
# Needs: DhruvAR/server/node_modules installed (tsx) and the Django .venv. Never touches DhruvAR/server/data.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
DJ_DIR="$(cd "$HERE/.." && pwd)"
NODE_DIR="${NODE_SERVER_DIR:-C:/Users/itcell/AndroidStudioProjects/DhruvAR/server}"
WORK="$(mktemp -d)"
TMP_NODE="$WORK/node-data"; TMP_DJ="$WORK/django-data"
mkdir -p "$TMP_NODE" "$TMP_DJ"
PY="$DJ_DIR/.venv/Scripts/python"
TSX="$NODE_DIR/node_modules/tsx/dist/cli.mjs"

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
  for port in 4100 4101; do
    pid=$(netstat -ano | grep ":$port " | grep LISTENING | awk '{print $5}' | head -1)
    [ -n "$pid" ] && taskkill //PID "$pid" //T //F >/dev/null 2>&1
  done
  sleep 1
  [ -n "$PGDB" ] && docker exec dhruvar-postgres psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS $PGDB WITH (FORCE)" >/dev/null 2>&1 && echo "--- throwaway PostgreSQL database dropped"
  rm -rf "$WORK"
  echo "--- temp folders removed: $( [ -e "$WORK" ] && echo NO || echo yes ); ports still listening: $(netstat -ano | grep -E ':(4100|4101) ' | grep -c LISTENING)"
}
trap cleanup EXIT

echo "=== seeding"
(cd "$NODE_DIR" && DATA_DIR="$TMP_NODE" node "$TSX" scripts/seed.ts) || exit 2
export DATA_DIR="$TMP_DJ"
(cd "$DJ_DIR" && "$PY" manage.py migrate >/dev/null && "$PY" manage.py seed) || exit 2
unset DATA_DIR

(cd "$NODE_DIR" && DATA_DIR="$TMP_NODE" PORT=4100 node "$TSX" src/index.ts > "$WORK/node.log" 2>&1) &
(cd "$DJ_DIR" && DATA_DIR="$TMP_DJ" ALLOWED_HOSTS=127.0.0.1,localhost "$PY" manage.py runserver 4101 --noreload > "$WORK/django.log" 2>&1) &
for i in $(seq 1 40); do
  a=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:4100/api/health)
  b=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:4101/api/health)
  [ "$a" = "200" ] && [ "$b" = "200" ] && break
  sleep 1
done
[ "$a" = "200" ] && [ "$b" = "200" ] || { echo "servers did not start (node=$a django=$b)"; exit 3; }

echo; echo "=== 1. Node seed vs Django seed"
"$PY" "$HERE/compare_seed.py" --node-url http://127.0.0.1:4100 --node-creds "$TMP_NODE/seed-credentials.txt" \
  --django-url http://127.0.0.1:4101 --django-creds "$TMP_DJ/seed-credentials.txt"
SEEDCMP=$?

cd "$DJ_DIR"
echo; echo "=== 2. api-test.ts against NODE (external mode)"
API_BASE=http://127.0.0.1:4100/api SEED_CREDENTIALS="$TMP_NODE/seed-credentials.txt" \
TAMPER_COMMAND="node $(cygpath -m "$HERE")/tamper_audit_node.mjs $(cygpath -m "$TMP_NODE")/dhruvar.db" \
  node "$TSX" --test scripts/api-test.ts 2>&1 | grep -E "${TEST_FILTER:-^(✔|✖|ℹ (tests|pass|fail))}"
NODERES=${PIPESTATUS[0]}

echo; echo "=== 3. api-test.ts against DJANGO (the same file)"
API_BASE=http://127.0.0.1:4101/api SEED_CREDENTIALS="$TMP_DJ/seed-credentials.txt" \
DATA_DIR="$TMP_DJ" TAMPER_COMMAND="$(cygpath -m "$PY") $(cygpath -m "$HERE")/tamper_audit_django.py" \
  node "$TSX" --test scripts/api-test.ts 2>&1 | grep -E "${TEST_FILTER:-^(✔|✖|ℹ (tests|pass|fail))}"
DJRES=${PIPESTATUS[0]}

echo; echo "exit codes: seed-compare=$SEEDCMP node-tests=$NODERES django-tests=$DJRES"
[ "$SEEDCMP" = 0 ] && [ "$NODERES" = 0 ] && [ "$DJRES" = 0 ]
