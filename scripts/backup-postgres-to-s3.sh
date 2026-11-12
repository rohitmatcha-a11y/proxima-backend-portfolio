#!/usr/bin/env bash
# Creates a consistent PostgreSQL archive, validates it, and uploads it to S3.
#
# This script is for the root-owned Ubuntu production host only. It uses the
# EC2 instance profile for short-lived AWS credentials; do not add access keys
# to .env.production or to this script.

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly DEPLOY_DIR="${JOBZ_DEPLOY_DIR:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
readonly COMPOSE_FILE="${JOBZ_COMPOSE_FILE:-${DEPLOY_DIR}/docker-compose.production.yml}"
readonly ENV_FILE="${JOBZ_ENV_FILE:-${DEPLOY_DIR}/.env.production}"
readonly BACKUP_DIR="${JOBZ_BACKUP_DIR:-/var/backups/jobz}"
readonly POSTGRES_IMAGE="${JOBZ_POSTGRES_IMAGE:-postgres:16}"

dump_file=""
checksum_file=""

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

cleanup() {
  rm -f -- "${dump_file}" "${checksum_file}"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command is unavailable: $1"
}

if [[ "${EUID}" -ne 0 ]]; then
  fail "Run this script as root through System Manager or sudo."
fi

: "${BACKUP_S3_BUCKET:?Set BACKUP_S3_BUCKET in /etc/jobz/backup.env}"
: "${AWS_REGION:?Set AWS_REGION in /etc/jobz/backup.env}"

if [[ "${BACKUP_S3_BUCKET}" == *"/"* ]]; then
  fail "BACKUP_S3_BUCKET must be a bucket name, not an S3 URI."
fi

backup_prefix="${BACKUP_S3_PREFIX:-postgres}"
backup_prefix="${backup_prefix#/}"
backup_prefix="${backup_prefix%/}"
[[ -n "${backup_prefix}" ]] || fail "BACKUP_S3_PREFIX must not be empty."

for required in aws basename date docker flock mktemp sha256sum; do
  require_command "${required}"
done

[[ -f "${COMPOSE_FILE}" ]] || fail "Compose file was not found: ${COMPOSE_FILE}"
[[ -r "${ENV_FILE}" ]] || fail "Production environment file is not readable: ${ENV_FILE}"

# The production environment file is passed only to Docker Compose. Do not
# source it here: it contains application secrets and may use Compose syntax.
compose=(
  docker compose
  --project-directory "${DEPLOY_DIR}"
  --env-file "${ENV_FILE}"
  -f "${COMPOSE_FILE}"
)

export AWS_DEFAULT_REGION="${AWS_REGION}"
umask 077
install -d -o root -g root -m 0700 "${BACKUP_DIR}"

exec 9>"${BACKUP_DIR}/postgres-backup.lock"
flock -n 9 || fail "Another PostgreSQL backup is already running."

printf '%s\n' 'Checking PostgreSQL readiness...'
"${compose[@]}" exec -T postgres sh -c 'pg_isready --quiet -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  || fail "PostgreSQL is not ready; no backup was created."

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
date_path="$(date -u +%Y/%m/%d)"
archive_name="jobz-postgres-${timestamp}.dump"
dump_file="$(mktemp "${BACKUP_DIR}/.${archive_name}.XXXXXX")"
checksum_file="${dump_file}.sha256"
trap cleanup EXIT

printf 'Creating PostgreSQL archive %s...\n' "${archive_name}"
if ! "${compose[@]}" exec -T postgres sh -c \
  'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --compress=9 --no-owner --no-privileges' \
  >"${dump_file}"; then
  fail "pg_dump failed."
fi

[[ -s "${dump_file}" ]] || fail "pg_dump produced an empty archive."

printf '%s\n' 'Validating the PostgreSQL archive format...'
docker run --rm --network none \
  --volume "${BACKUP_DIR}:/backup:ro" \
  "${POSTGRES_IMAGE}" \
  pg_restore --list --format=custom "/backup/$(basename "${dump_file}")" >/dev/null \
  || fail "pg_restore could not validate the archive."

sha256sum "${dump_file}" >"${checksum_file}"

object_key="${backup_prefix}/${date_path}/${archive_name}"
object_uri="s3://${BACKUP_S3_BUCKET}/${object_key}"

printf 'Uploading encrypted archive to %s...\n' "${object_uri}"
aws s3 cp "${dump_file}" "${object_uri}" --only-show-errors --sse AES256
aws s3 cp "${checksum_file}" "${object_uri}.sha256" --only-show-errors --sse AES256

printf 'PostgreSQL backup completed: %s\n' "${object_uri}"
