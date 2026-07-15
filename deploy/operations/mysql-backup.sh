#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" == "--help" ]]; then
  printf '%s\n' "Usage: BACKUP_DIR=/secure/path RETENTION_DAYS=14 deploy/operations/mysql-backup.sh"
  exit 0
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
backup_dir="${BACKUP_DIR:-${repo_root}/backups/mysql}"
retention_days="${RETENTION_DAYS:-14}"

if ! [[ "${retention_days}" =~ ^[0-9]+$ ]] || (( retention_days < 1 )); then
  printf '%s\n' "RETENTION_DAYS must be a positive integer." >&2
  exit 2
fi

umask 077
mkdir -p "${backup_dir}"
timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
file_name="gray-anime-${timestamp}.sql.gz"
temporary_file="${backup_dir}/.${file_name}.partial"
final_file="${backup_dir}/${file_name}"
checksum_file="${final_file}.sha256"
completed=false

cleanup() {
  rm -f -- "${temporary_file}"
  if [[ "${completed}" != "true" ]]; then
    rm -f -- "${final_file}" "${checksum_file}"
  fi
}
trap cleanup EXIT

docker compose exec -T mysql sh -ec '
  MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump \
    --user="$MYSQL_USER" \
    --host=127.0.0.1 \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --default-character-set=utf8mb4 \
    --set-gtid-purged=OFF \
    --no-tablespaces \
    "$MYSQL_DATABASE"
' | gzip -9 > "${temporary_file}"

gzip -t "${temporary_file}"
if [[ ! -s "${temporary_file}" ]]; then
  printf '%s\n' "Backup output is empty." >&2
  exit 1
fi

mv -- "${temporary_file}" "${final_file}"
(
  cd "${backup_dir}"
  sha256sum "${file_name}" > "${file_name}.sha256"
)

find "${backup_dir}" -maxdepth 1 -type f \( -name 'gray-anime-*.sql.gz' -o -name 'gray-anime-*.sql.gz.sha256' \) \
  -mtime "+${retention_days}" -delete

completed=true
trap - EXIT
printf 'Backup created: %s\n' "${final_file}"
