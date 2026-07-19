#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${GRAY_PROJECT_DIR:-/opt/gray}"
ENV_FILE="${GRAY_ENV_FILE:-/etc/gray/gray.env}"

cd "$PROJECT_DIR"
docker compose --env-file "$ENV_FILE" \
  -f docker-compose.yml \
  -f docker-compose.production.yml \
  -f docker-compose.demo.yml \
  -f docker-compose.public.yml \
  exec -T frontend nginx -s reload
