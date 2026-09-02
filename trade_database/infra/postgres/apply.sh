#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

source "${REPO_DIR}/.env"

export PGPASSWORD="${POSTGRES_PASSWORD}"

psql \
    -h "${POSTGRES_HOST:-localhost}" \
    -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER:-postgres}" \
    -d "${TARGET_DATABASE:-${POSTGRES_DB}}" \
    -v ON_ERROR_STOP=1 \
    -f "${REPO_DIR}/Database/schema/schema.sql"

psql \
    -h "${POSTGRES_HOST:-localhost}" \
    -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER:-postgres}" \
    -d "${TARGET_DATABASE:-${POSTGRES_DB}}" \
    -v ON_ERROR_STOP=1 \
    -f "${REPO_DIR}/Database/seed/001_seed.sql"

echo "Database setup complete."