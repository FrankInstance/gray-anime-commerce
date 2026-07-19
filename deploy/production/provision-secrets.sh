#!/usr/bin/env bash
set -Eeuo pipefail

PUBLIC_DOMAIN="${1:?Usage: sudo bash provision-secrets.sh <public-domain> [config-directory]}"
CONFIG_DIR="${2:-/etc/gray}"
PRIVATE_KEY_FILE="$CONFIG_DIR/access-token-private.pem"
PUBLIC_KEY_FILE="$CONFIG_DIR/access-token-public.pem"
ENV_FILE="$CONFIG_DIR/gray.env"

if [[ "$EUID" -ne 0 ]]; then
  echo "Run this script with sudo." >&2
  exit 1
fi

install -d -m 0700 "$CONFIG_DIR"

if [[ -e "$PRIVATE_KEY_FILE" || -e "$PUBLIC_KEY_FILE" ]]; then
  if [[ ! -f "$PRIVATE_KEY_FILE" || ! -f "$PUBLIC_KEY_FILE" ]]; then
    echo "Authentication key pair is incomplete in $CONFIG_DIR." >&2
    exit 1
  fi
else
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$PRIVATE_KEY_FILE"
  openssl pkey -in "$PRIVATE_KEY_FILE" -pubout -out "$PUBLIC_KEY_FILE"
  chmod 0600 "$PRIVATE_KEY_FILE"
  chmod 0644 "$PUBLIC_KEY_FILE"
fi

if [[ -f "$ENV_FILE" ]]; then
  echo "$ENV_FILE already exists; existing deployment secrets were preserved."
  exit 0
fi

umask 077
TEMP_ENV="$(mktemp "$CONFIG_DIR/gray.env.XXXXXX")"
trap 'rm -f "$TEMP_ENV"' EXIT

printf '%s\n' \
  "PUBLIC_DOMAIN=$PUBLIC_DOMAIN" \
  "LETSENCRYPT_EMAIL=replace-with-your-email@example.com" \
  "MYSQL_ROOT_PASSWORD=$(openssl rand -hex 32)" \
  "MYSQL_PASSWORD=$(openssl rand -hex 32)" \
  "RABBITMQ_PASSWORD=$(openssl rand -hex 32)" \
  "MINIO_ROOT_USER=grayadmin" \
  "MINIO_ROOT_PASSWORD=$(openssl rand -hex 32)" \
  "JWT_PRIVATE_KEY_FILE=$PRIVATE_KEY_FILE" \
  "JWT_PUBLIC_KEY_FILE=$PUBLIC_KEY_FILE" \
  "JWT_ISSUER=https://$PUBLIC_DOMAIN" \
  "JWT_AUDIENCE=gray-api" \
  "JWT_KEY_ID=gray-access-2026-07" \
  "AUTH_LOGIN_THROTTLE_SECRET=$(openssl rand -hex 32)" \
  "CORS_ALLOWED_ORIGIN_PATTERNS=https://$PUBLIC_DOMAIN" \
  > "$TEMP_ENV"

install -m 0600 "$TEMP_ENV" "$ENV_FILE"
echo "Created protected deployment secrets in $ENV_FILE."
