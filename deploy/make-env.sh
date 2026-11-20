#!/bin/sh
# Creates deploy/.env with random secrets if it does not exist. Prints nothing secret.
cd "$(dirname "$0")"
[ -e .env ] && { echo ".env already exists; not touching it"; exit 0; }
rnd() { python3 -c "import secrets;print(secrets.token_urlsafe(48))" 2>/dev/null || python -c "import secrets;print(secrets.token_urlsafe(48))"; }
sed -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(rnd)/" -e "s/^DJANGO_SECRET_KEY=.*/DJANGO_SECRET_KEY=$(rnd)/" .env.example > .env
echo "created deploy/.env (random secrets, not shown). Edit LAN_HOST in it."
