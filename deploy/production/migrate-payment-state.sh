#!/usr/bin/env bash
set -Eeuo pipefail

if (( EUID != 0 )); then
  printf '%s\n' "Run this script with sudo." >&2
  exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${GRAY_ENV_FILE:-/etc/gray/gray.env}"
migration="deploy/mysql/migrations/20260719-payment-state-machine.sql"
compose=(
  docker compose
  --env-file "${env_file}"
  -f docker-compose.yml
  -f docker-compose.production.yml
)

cd "${repo_root}"
test -r "${env_file}"
test -r "${migration}"

"${compose[@]}" cp "${migration}" mysql:/tmp/20260719-payment-state-machine.sql
"${compose[@]}" exec -T mysql sh -ec '
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --default-character-set=utf8mb4 \
    --user="$MYSQL_USER" \
    --database="$MYSQL_DATABASE" \
    < /tmp/20260719-payment-state-machine.sql
'

"${compose[@]}" exec -T mysql sh -ec '
  export MYSQL_PWD="$MYSQL_PASSWORD"
  column_count="$(mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" \
    --batch --skip-column-names --execute="
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND (
          (table_name = 0x6f7264657273 AND column_name IN (
            0x66756c66696c6c6d656e745f737461747573,
            0x63616e63656c5f726561736f6e,
            0x706169645f6174,
            0x63616e63656c6c65645f6174
          ))
          OR
          (table_name = 0x7061796d656e74 AND column_name IN (
            0x70726f76696465725f73657373696f6e5f6964,
            0x73657373696f6e5f657870697265735f6174,
            0x6661696c7572655f636f6465,
            0x617474656d70745f636f756e74,
            0x757064617465645f6174
          ))
        )")"
  table_count="$(mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" \
    --batch --skip-column-names --execute="
      SELECT COUNT(*)
      FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name IN (0x696e626f785f6576656e74, 0x7061796d656e745f7472616e736974696f6e)")"
  if [ "$column_count" != "9" ] || [ "$table_count" != "2" ]; then
    printf "Migration verification failed: columns=%s tables=%s\n" "$column_count" "$table_count" >&2
    exit 1
  fi
  printf "Payment migration verified: columns=%s tables=%s\n" "$column_count" "$table_count"
'
