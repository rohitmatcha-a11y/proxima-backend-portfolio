#!/usr/bin/env bash

# Verify that a brand-new production-style database reaches a healthy backend
# without using this machine's development containers, volumes, or secrets.
#
# Run from any machine with Docker:
#   bash scripts/verify-fresh-production-db.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_NAME="jobz-fresh-db-verify"
COMPOSE=(docker compose --project-name "$PROJECT_NAME" --env-file /dev/null -f "$ROOT_DIR/docker-compose.production.yml")

export DOMAIN="fresh-db-preflight.invalid"
export ACME_EMAIL="ops@fresh-db-preflight.invalid"
export FRONTEND_BUILD_CONTEXT="${FRONTEND_BUILD_CONTEXT:-$ROOT_DIR/../proxima-frontend}"
export APP_VERSION="fresh-db-preflight"
export POSTGRES_DB="jobportal_preflight"
export POSTGRES_USER="jobportal_preflight"
export POSTGRES_PASSWORD="preflight-postgres-password"
export APP_AUTH_JWT_SECRET="preflight-jwt-secret-that-is-longer-than-thirty-two-characters"
export ANTHROPIC_API_KEY="preflight-not-a-real-key"
export GOOGLE_CLIENT_ID="preflight-google-client-id"
export GITHUB_CLIENT_ID="preflight-github-client-id"
export GITHUB_CLIENT_SECRET="preflight-github-client-secret"
export APP_AUTH_BOOTSTRAP_ADMIN_ENABLED="false"
export APP_SCRAPER_ENABLED="false"
export APP_SEED_ENABLED="false"

cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}

on_error() {
  echo >&2 "Fresh database verification failed. Backend logs follow:"
  "${COMPOSE[@]}" logs --tail=200 backend >&2 || true
}

trap cleanup EXIT
trap on_error ERR

if ! docker info >/dev/null 2>&1; then
  echo >&2 "Docker is not running. Start Docker Desktop, then run this command again."
  exit 1
fi

cd "$ROOT_DIR"

"${COMPOSE[@]}" up --build --wait --wait-timeout 300 \
  postgres redis zookeeper kafka elasticsearch backend

required_tables=(
  companies jobs scraper_configs scraper_runs job_views
  outbox_events processed_events continent_mappings geo_locations
  app_users user_saved_jobs user_job_applications feedback_submissions
)

for table in "${required_tables[@]}"; do
  exists="$("${COMPOSE[@]}" exec -T postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
    "SELECT to_regclass('public.${table}') IS NOT NULL")"
  if [[ "$exists" != "t" ]]; then
    echo >&2 "Expected table '${table}' was not created."
    exit 1
  fi
done

changeset_count="$("${COMPOSE[@]}" exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "SELECT COUNT(*) FROM databasechangelog")"
if [[ "$changeset_count" != "18" ]]; then
  echo >&2 "Expected 18 Liquibase changesets, found ${changeset_count}."
  exit 1
fi

timestamp_type="$("${COMPOSE[@]}" exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "SELECT data_type FROM information_schema.columns WHERE table_name = 'jobs' AND column_name = 'created_at'")"
if [[ "$timestamp_type" != "timestamp with time zone" ]]; then
  echo >&2 "Expected jobs.created_at to be TIMESTAMPTZ after V001, found '${timestamp_type}'."
  exit 1
fi

required_final_columns=(size_category location_text)
for column in "${required_final_columns[@]}"; do
  exists="$("${COMPOSE[@]}" exec -T postgres \
    psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
    "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'jobs' AND column_name = '${column}')")"
  if [[ "$column" == "size_category" ]]; then
    exists="$("${COMPOSE[@]}" exec -T postgres \
      psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
      "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'companies' AND column_name = 'size_category')")"
  fi
  if [[ "$exists" != "t" ]]; then
    echo >&2 "Expected final column '${column}' was not created."
    exit 1
  fi
done

echo "Fresh production database verification passed."
