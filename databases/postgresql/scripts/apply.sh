#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ ! -f "${DB_DIR}/.env" ]; then
    echo "Error: .env file not found at ${DB_DIR}/.env"
    echo "Please create a .env file. You can copy from .env.example:"
    echo "  cp ${DB_DIR}/.env.example ${DB_DIR}/.env"
    exit 1
fi

source "${DB_DIR}/.env"

export PGPASSWORD="${POSTGRES_PASSWORD}"

# schema.sql is the baseline (formerly V1); migrations/ holds V2 onwards.
echo "Running migrations..."
for migration_file in "${DB_DIR}/schema.sql" $(find "${DB_DIR}/migrations" -name "V*.sql" | sort -V); do
    echo "Applying migration: $(basename "$migration_file")"
    psql \
        -h "${POSTGRES_HOST:-localhost}" \
        -p "${POSTGRES_PORT:-5432}" \
        -U "${POSTGRES_USER:-postgres}" \
        -d "${TARGET_DATABASE:-${POSTGRES_DB}}" \
        -v ON_ERROR_STOP=1 \
        -f "$migration_file"
done

echo "Applying seed data (idempotent, ignoring conflicts)..."

# Create temporary seed file with ON CONFLICT clauses for idempotency.
# This allows re-running apply.sh without "relation already exists" errors.
seed_file="${DB_DIR}/seed-data.sql"
temp_seed_file=$(mktemp)

# Transform lines ending with ); to add ON CONFLICT DO NOTHING before the semicolon.
# Only add if ON CONFLICT is not already present on the line.
sed '/ON CONFLICT/! s/);$/)\nON CONFLICT DO NOTHING;/' "$seed_file" > "$temp_seed_file"

psql \
    -h "${POSTGRES_HOST:-localhost}" \
    -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER:-postgres}" \
    -d "${TARGET_DATABASE:-${POSTGRES_DB}}" \
    -f "$temp_seed_file" 2>&1 | grep -v "duplicate key value" || true

rm "$temp_seed_file"

echo "Database setup complete."