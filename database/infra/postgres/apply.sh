#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

if [ ! -f "${REPO_DIR}/.env" ]; then
    echo "Error: .env file not found at ${REPO_DIR}/.env"
    echo "Please create a .env file. You can copy from .env.example:"
    echo "  cp ${REPO_DIR}/.env.example ${REPO_DIR}/.env"
    exit 1
fi

source "${REPO_DIR}/.env"

export PGPASSWORD="${POSTGRES_PASSWORD}"

echo "Running migrations..."
for migration_file in $(find "${REPO_DIR}/database/migration" -name "V*.sql" | sort -V); do
    echo "Applying migration: $(basename "$migration_file")"
    psql \
        -h "${POSTGRES_HOST:-localhost}" \
        -p "${POSTGRES_PORT:-5432}" \
        -U "${POSTGRES_USER:-postgres}" \
        -d "${TARGET_DATABASE:-${POSTGRES_DB}}" \
        -v ON_ERROR_STOP=1 \
        -f "$migration_file"
done

psql \
    -h "${POSTGRES_HOST:-localhost}" \
    -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER:-postgres}" \
    -d "${TARGET_DATABASE:-${POSTGRES_DB}}" \
    -v ON_ERROR_STOP=1 \
    -f "${REPO_DIR}/database/seed/001_seed.sql"

echo "Database setup complete."