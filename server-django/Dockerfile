# Django API for DhruvAR, run by gunicorn. Build context is server-django/.
# Plain words: an "image" is the recipe-built package; a "container" is one running copy of it.
FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 DATA_DIR=/data

WORKDIR /app
COPY requirements.txt requirements-prod.txt ./
RUN pip install --no-cache-dir -r requirements-prod.txt

COPY . .

# Non-root user; /data is the volume that keeps uploads, the generated secret key and seed passwords.
RUN useradd --system --uid 10001 --home /app dhruvar \
 && mkdir -p /data && chown dhruvar /data \
 && chmod +x /app/docker-entrypoint.sh
USER dhruvar
VOLUME /data
EXPOSE 8000

ENTRYPOINT ["/app/docker-entrypoint.sh"]
