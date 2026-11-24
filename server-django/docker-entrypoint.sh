#!/bin/sh
# Runs on every container start: bring the database up to date, then serve with gunicorn.
set -e
python manage.py migrate --noinput
# 3 worker processes x 2 threads. Locks for the audit chain, plan codes and sign-in throttle live in
# PostgreSQL, so several workers are safe. Keep workers x threads below PostgreSQL's max_connections (100).
exec gunicorn config.wsgi:application \
  --bind 0.0.0.0:8000 \
  --workers "${GUNICORN_WORKERS:-3}" --threads "${GUNICORN_THREADS:-2}" \
  --timeout 60 --access-logfile - --error-logfile -
