#!/usr/bin/env bash

# Start an isolated production-shaped stack and prove the browser-facing Caddy
# routes work end-to-end. It uses dummy credentials, disables seeding/scraping,
# binds only an alternate local HTTP port, and deletes its own resources.
set -euo pipefail

PROJECT_NAME="jobz-full-stack-verify"
SMOKE_HTTP_PORT="${SMOKE_HTTP_PORT:-18080}"
BASE_URL="http://127.0.0.1:${SMOKE_HTTP_PORT}"
COMPOSE=(
  docker compose
  --env-file /dev/null
  --project-name "${PROJECT_NAME}"
  -f docker-compose.production.yml
  -f docker-compose.production.smoke.yml
)

cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans --rmi local >/dev/null 2>&1 || true
}

on_error() {
  printf '\nFull production stack verification failed. Relevant container logs follow.\n' >&2
  "${COMPOSE[@]}" logs --no-color >&2 || true
}

trap cleanup EXIT
trap on_error ERR

export DOMAIN=localhost
export ACME_EMAIL=smoke@example.com
export POSTGRES_DB=jobportal
export POSTGRES_USER=jobportal
export POSTGRES_PASSWORD=smoke-postgres-password
export ANTHROPIC_API_KEY=smoke-anthropic-key
export APP_AUTH_JWT_SECRET=smoke-jwt-secret-long-enough-for-production
export GOOGLE_CLIENT_ID=dummy.apps.googleusercontent.com
export GITHUB_CLIENT_ID=dummy
export GITHUB_CLIENT_SECRET=dummy
export APP_SCRAPER_ENABLED=false
export APP_SEED_ENABLED=false
export APP_AUTH_BOOTSTRAP_ADMIN_ENABLED=false
export SENTRY_DSN=
export SENTRY_AUTH_TOKEN=
export SENTRY_ORG=
export SENTRY_PROJECT=

"${COMPOSE[@]}" up --build --wait --wait-timeout 300

assert_header() {
  local response_headers="$1"
  local expected="$2"

  if ! grep -qi "^${expected}" <<<"${response_headers}"; then
    printf 'Expected response header was not present: %s\n' "${expected}" >&2
    printf 'Received headers:\n%s\n' "${response_headers}" >&2
    exit 1
  fi
}

homepage_headers="$(curl --fail --silent --show-error --retry 10 --retry-connrefused --retry-delay 2 --head "${BASE_URL}/")"
assert_header "${homepage_headers}" "content-security-policy:"
assert_header "${homepage_headers}" "x-content-type-options: nosniff"
assert_header "${homepage_headers}" "x-frame-options: DENY"
assert_header "${homepage_headers}" "referrer-policy: strict-origin-when-cross-origin"
assert_header "${homepage_headers}" "permissions-policy:"
assert_header "${homepage_headers}" "cross-origin-opener-policy: same-origin-allow-popups"

homepage="$(curl --fail --silent --show-error --retry 10 --retry-connrefused --retry-delay 2 "${BASE_URL}/")"
[[ "${homepage}" == *"Proxima"* ]] || {
  printf 'The browser-facing homepage did not contain the expected application marker.\n' >&2
  exit 1
}

health="$(curl --fail --silent --show-error --retry 10 --retry-connrefused --retry-delay 2 "${BASE_URL}/api/v1/health")"
[[ "${health}" == *'"status":"UP"'* ]] || {
  printf 'The direct backend health route did not report UP.\n' >&2
  exit 1
}

# Spring Security deliberately permits public GET health checks, not HEAD.
# Capture response headers from that allowed request instead of broadening the
# production security policy merely for the verifier.
health_headers="$(curl --fail --silent --show-error --dump-header - --output /dev/null "${BASE_URL}/api/v1/health")"
assert_header "${health_headers}" "cache-control: no-store"

stats="$(curl --fail --silent --show-error "${BASE_URL}/api/backend/jobs/stats")"
[[ "${stats}" == *'"totalJobs"'* && "${stats}" == *'"totalCompanies"'* ]] || {
  printf 'The browser API prefix did not return job statistics through Caddy.\n' >&2
  exit 1
}

printf 'Full production stack verification passed.\n'
