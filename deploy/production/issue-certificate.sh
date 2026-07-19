#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${GRAY_PROJECT_DIR:-/opt/gray}"
ENV_FILE="${GRAY_ENV_FILE:-/etc/gray/gray.env}"
WEBROOT="/opt/gray/certbot/www"

if [[ "$EUID" -ne 0 ]]; then
  echo "Run this script with sudo." >&2
  exit 1
fi
if [[ ! -r "$ENV_FILE" ]]; then
  echo "Deployment environment file is not readable: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${PUBLIC_DOMAIN:?PUBLIC_DOMAIN is required in $ENV_FILE}"
: "${LETSENCRYPT_EMAIL:?LETSENCRYPT_EMAIL is required in $ENV_FILE}"

install -d -m 0755 "$WEBROOT"
certbot certonly --webroot --webroot-path "$WEBROOT" \
  --domain "$PUBLIC_DOMAIN" --domain "www.$PUBLIC_DOMAIN" \
  --email "$LETSENCRYPT_EMAIL" --agree-tos --non-interactive --keep-until-expiring

cd "$PROJECT_DIR"
compose=(docker compose --env-file "$ENV_FILE"
  -f docker-compose.yml
  -f docker-compose.production.yml
  -f docker-compose.demo.yml
  -f docker-compose.public.yml)
"${compose[@]}" exec -T frontend /docker-entrypoint.d/40-gray-public-config.sh
"${compose[@]}" exec -T frontend nginx -s reload

echo "Certificate is active for $PUBLIC_DOMAIN and www.$PUBLIC_DOMAIN."
