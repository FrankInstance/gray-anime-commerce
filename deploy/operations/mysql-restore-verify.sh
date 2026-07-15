#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" == "--help" ]]; then
  printf '%s\n' "Usage: deploy/operations/mysql-restore-verify.sh [backup.sql.gz]"
  exit 0
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
backup_dir="${BACKUP_DIR:-${repo_root}/backups/mysql}"
backup_file="${1:-}"

if [[ -z "${backup_file}" ]]; then
  backup_file="$(find "${backup_dir}" -maxdepth 1 -type f -name 'gray-anime-*.sql.gz' -print | sort | tail -n 1)"
fi
if [[ -z "${backup_file}" || ! -f "${backup_file}" ]]; then
  printf '%s\n' "A readable .sql.gz backup is required." >&2
  exit 2
fi

gzip -t "${backup_file}"
checksum_file="${backup_file}.sha256"
if [[ ! -f "${checksum_file}" ]]; then
  printf 'Checksum file is missing: %s\n' "${checksum_file}" >&2
  exit 2
fi
(
  cd "$(dirname "${backup_file}")"
  sha256sum -c "$(basename "${checksum_file}")"
)

verify_database="gray_restore_verify_$(date -u +'%Y%m%d%H%M%S')_$$"
if ! [[ "${verify_database}" =~ ^[a-zA-Z0-9_]+$ ]]; then
  printf '%s\n' "Generated verification database name is invalid." >&2
  exit 1
fi

drop_verification_database() {
  docker compose exec -T -e VERIFY_DATABASE="${verify_database}" mysql sh -ec '
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --host=127.0.0.1 \
      --execute="DROP DATABASE IF EXISTS \`$VERIFY_DATABASE\`"
  ' >/dev/null 2>&1 || true
}
trap drop_verification_database EXIT

docker compose exec -T -e VERIFY_DATABASE="${verify_database}" mysql sh -ec '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --host=127.0.0.1 \
    --execute="CREATE DATABASE \`$VERIFY_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
'

gzip -dc "${backup_file}" | docker compose exec -T -e VERIFY_DATABASE="${verify_database}" mysql sh -ec '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --host=127.0.0.1 \
    --default-character-set=utf8mb4 "$VERIFY_DATABASE"
'

docker compose exec -T -e VERIFY_DATABASE="${verify_database}" mysql sh -ec '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  table_names="$(mysql --user=root --host=127.0.0.1 --batch --skip-column-names \
    --database="$VERIFY_DATABASE" \
    --execute="SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 0x42415345205441424c45")"
  while IFS= read -r table_name; do
    [ -n "$table_name" ] || continue
    case "$table_name" in
      *[!a-zA-Z0-9_]*)
        printf "Unsafe table name in restored database: %s\n" "$table_name" >&2
        exit 1
        ;;
    esac
    check_status="$(mysql --user=root --host=127.0.0.1 --batch --skip-column-names \
      --database="$VERIFY_DATABASE" --execute="CHECK TABLE \`$table_name\`" | tail -n 1 | cut -f4)"
    if [ "$check_status" != "OK" ]; then
      printf "Table check failed: %s (%s)\n" "$table_name" "$check_status" >&2
      exit 1
    fi
  done <<EOF
$table_names
EOF
  source_tables="$(mysql --user=root --host=127.0.0.1 --batch --skip-column-names \
    --database="$MYSQL_DATABASE" \
    --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 0x42415345205441424c45")"
  restored_tables="$(mysql --user=root --host=127.0.0.1 --batch --skip-column-names \
    --database="$VERIFY_DATABASE" \
    --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 0x42415345205441424c45")"
  missing_core_tables="$(mysql --user=root --host=127.0.0.1 --batch --skip-column-names \
    --database="$VERIFY_DATABASE" \
    --execute="SELECT COUNT(*) FROM (SELECT 0x6170705f75736572 AS table_name UNION ALL SELECT 0x776f726b UNION ALL SELECT 0x70726f64756374 UNION ALL SELECT 0x6f7264657273 UNION ALL SELECT 0x7061796d656e74) expected LEFT JOIN information_schema.tables actual ON actual.table_schema = DATABASE() AND actual.table_name = expected.table_name WHERE actual.table_name IS NULL")"

  if [ "$source_tables" -lt 1 ] || [ "$source_tables" != "$restored_tables" ]; then
    printf "Table count mismatch: source=%s restored=%s\n" "$source_tables" "$restored_tables" >&2
    exit 1
  fi
  if [ "$missing_core_tables" != "0" ]; then
    printf "Restored database is missing %s core tables.\n" "$missing_core_tables" >&2
    exit 1
  fi
  printf "Restore verified: sourceTables=%s restoredTables=%s\n" "$source_tables" "$restored_tables"
'

drop_verification_database
trap - EXIT
printf 'Backup is restorable: %s\n' "${backup_file}"
