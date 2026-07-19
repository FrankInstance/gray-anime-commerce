#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${GRAY_PROJECT_DIR:-/opt/gray}"
HOOK_DIR="/etc/letsencrypt/renewal-hooks/deploy"

if [[ "$EUID" -ne 0 ]]; then
  echo "Run this script with sudo." >&2
  exit 1
fi

install -d -m 0755 "$HOOK_DIR"
install -m 0755 "$PROJECT_DIR/deploy/production/certbot-deploy-hook.sh" \
  "$HOOK_DIR/gray-nginx-reload"
systemctl enable --now certbot.timer
systemctl list-timers certbot.timer --no-pager

echo "Certbot renewal timer and Nginx reload hook are installed."
