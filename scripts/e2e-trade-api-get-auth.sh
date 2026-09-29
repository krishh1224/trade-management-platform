#!/usr/bin/env bash

# End-to-end integration test for the real Auth service and Trade API.
#
# Assumes the local platform stack is already running, typically via the root
# docker-compose platform profile, with:
#   - Nest auth service on http://localhost:3000
#   - Trade API on http://localhost:8081
#
# The script registers a fresh auth user bound to seeded account 101, logs in
# to obtain a real JWT, then exercises protected Trade API GET endpoints.

set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

AUTH_URL="${AUTH_URL:-http://localhost:3000}"
TRADE_URL="${TRADE_URL:-http://localhost:8081}"
ACCOUNT_ID="${ACCOUNT_ID:-101}"

RUN_ID="$(date +%Y%m%d%H%M%S)-$$-$RANDOM"
TEST_USER="${AUTH_USERNAME:-trade_get_e2e_${RUN_ID}}"
TEST_PASSWORD="${AUTH_PASSWORD:-Mission12345!}"

BAD_JWT_SECRET="${BAD_JWT_SECRET:-wrong-secret-not-trusted-by-platform-000001}"

ACCESS_TOKEN=""
PASS_COUNT=0
FAIL_COUNT=0

TMP_DIR="$(mktemp -d)"

cleanup() {
    rm -rf "$TMP_DIR"
}

trap cleanup EXIT

fail() {
    echo
    echo "FAIL: $1"
    exit 1
}

assert_status() {
    local actual="$1"
    local expected="$2"
    local test_name="$3"
    local body="${4:-}"

    if [[ "$actual" == "$expected" ]]; then
        echo "PASS: $test_name (HTTP $actual)"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "FAIL: $test_name (expected HTTP $expected, got HTTP $actual)"
        [[ -n "$body" ]] && echo "Response: $body"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        exit 1
    fi
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "'$1' is required but was not found."
}

validate_auth_inputs() {
    if [[ -n "${AUTH_USERNAME:-}" && -z "${AUTH_PASSWORD:-}" ]]; then
        fail "AUTH_PASSWORD must be set when AUTH_USERNAME is provided."
    fi

    if [[ -z "${AUTH_USERNAME:-}" && -n "${AUTH_PASSWORD:-}" ]]; then
        fail "AUTH_USERNAME must be set when AUTH_PASSWORD is provided."
    fi
}

wait_for_http_200() {
    local url="$1"
    local service_name="$2"
    local ready=0

    for _ in $(seq 1 60); do
        local code
        code="$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)"
        if [[ "$code" == "200" ]]; then
            ready=1
            break
        fi
        sleep 2
    done

    [[ "$ready" == "1" ]] || fail "$service_name did not become ready at $url within 120 seconds."
    echo "PASS: $service_name is ready"
}

json_register() {
    printf '{"username":"%s","password":"%s","accountId":%s}' "$1" "$2" "$3"
}

json_login() {
    printf '{"username":"%s","password":"%s"}' "$1" "$2"
}

json_value() {
    local key="$1"
    local file="$2"

    sed -n 's/.*"'$key'"[[:space:]]*:[[:space:]]*\("[^"]*"\|[^,}[:space:]]*\).*/\1/p' "$file" \
        | sed 's/^"//; s/"$//' \
        | head -n 1
}

assert_body_contains() {
    local file="$1"
    local pattern="$2"
    local test_name="$3"

    if grep -Eq "$pattern" "$file"; then
        echo "PASS: $test_name"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "FAIL: $test_name"
        echo "Response:"
        cat "$file"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        exit 1
    fi
}

forge_invalid_token() {
    local account_id="$1"

    ACCOUNT_ID="$account_id" BAD_JWT_SECRET="$BAD_JWT_SECRET" node <<'EOF'
const crypto = require('crypto');

const accountId = Number(process.env.ACCOUNT_ID);
const secret = process.env.BAD_JWT_SECRET;
const header = { typ: 'JWT', alg: 'HS256' };
const payload = {
  accountId,
  exp: Math.floor(Date.now() / 1000) + 600,
};

const base64url = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
const signingInput = `${base64url(header)}.${base64url(payload)}`;
const signature = crypto.createHmac('sha256', secret).update(signingInput).digest('base64url');

process.stdout.write(`${signingInput}.${signature}`);
EOF
}

echo "=================================================="
echo " Trade API GET Auth Integration Test"
echo "=================================================="
echo "Auth URL     : $AUTH_URL"
echo "Trade URL    : $TRADE_URL"
echo "Account ID   : $ACCOUNT_ID"
echo "Test User    : $TEST_USER"
if [[ -n "${AUTH_USERNAME:-}" ]]; then
    echo "Auth Mode    : login with existing credentials"
else
    echo "Auth Mode    : register a fresh credential, then log in"
fi
echo "=================================================="

require_command curl
require_command node
validate_auth_inputs

wait_for_http_200 "$AUTH_URL/health" "Nest auth service"
wait_for_http_200 "$TRADE_URL/health" "Trade API"

if [[ -z "${AUTH_USERNAME:-}" ]]; then
    echo
    echo ">>> TEST 1: Register a fresh auth user for seeded account $ACCOUNT_ID"

    REGISTER_BODY="$(json_register "$TEST_USER" "$TEST_PASSWORD" "$ACCOUNT_ID")"
    REGISTER_STATUS="$(curl -sS \
        -o "$TMP_DIR/register.json" \
        -w "%{http_code}" \
        -X POST "$AUTH_URL/auth/register" \
        -H "Content-Type: application/json" \
        -d "$REGISTER_BODY")"
    REGISTER_RESPONSE="$(cat "$TMP_DIR/register.json")"

    if [[ "$REGISTER_STATUS" == "409" ]]; then
        fail "Auth register returned 409. The endpoint is being hit, but account $ACCOUNT_ID already has a credential because credentials.account_id is unique. Re-run with AUTH_USERNAME and AUTH_PASSWORD for that existing account, or remove that credentials row before retrying registration. Response: $REGISTER_RESPONSE"
    fi

    assert_status "$REGISTER_STATUS" "201" "Auth register" "$REGISTER_RESPONSE"
else
    echo
    echo ">>> TEST 1: Skipping registration and using existing auth credentials"
    echo "PASS: registration skipped because AUTH_USERNAME/AUTH_PASSWORD were provided"
    PASS_COUNT=$((PASS_COUNT + 1))
fi

echo
echo ">>> TEST 2: Log in and obtain a real JWT"

LOGIN_BODY="$(json_login "$TEST_USER" "$TEST_PASSWORD")"
LOGIN_STATUS="$(curl -sS \
    -o "$TMP_DIR/login.json" \
    -w "%{http_code}" \
    -X POST "$AUTH_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "$LOGIN_BODY")"
LOGIN_RESPONSE="$(cat "$TMP_DIR/login.json")"

assert_status "$LOGIN_STATUS" "200" "Auth login" "$LOGIN_RESPONSE"

ACCESS_TOKEN="$(json_value "accessToken" "$TMP_DIR/login.json")"
[[ "$ACCESS_TOKEN" != "null" && -n "$ACCESS_TOKEN" ]] || fail "Login response did not contain accessToken."

echo "PASS: access token received"

echo
echo ">>> TEST 3: Trade health endpoint stays public"

HEALTH_STATUS="$(curl -sS \
    -o "$TMP_DIR/health.json" \
    -w "%{http_code}" \
    "$TRADE_URL/health")"
HEALTH_RESPONSE="$(cat "$TMP_DIR/health.json")"

assert_status "$HEALTH_STATUS" "200" "Trade health endpoint" "$HEALTH_RESPONSE"

echo
echo ">>> TEST 4: Protected Trade GET without a token must be refused"

NO_TOKEN_STATUS="$(curl -sS \
    -o "$TMP_DIR/no-token.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID")"
NO_TOKEN_RESPONSE="$(cat "$TMP_DIR/no-token.json")"

assert_status "$NO_TOKEN_STATUS" "401" "Trade GET without token" "$NO_TOKEN_RESPONSE"

echo
echo ">>> TEST 5: Protected Trade GET with a forged token must be refused"

INVALID_TOKEN="$(forge_invalid_token "$ACCOUNT_ID")"
INVALID_TOKEN_STATUS="$(curl -sS \
    -o "$TMP_DIR/invalid-token.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID" \
    -H "Authorization: Bearer $INVALID_TOKEN")"
INVALID_TOKEN_RESPONSE="$(cat "$TMP_DIR/invalid-token.json")"

assert_status "$INVALID_TOKEN_STATUS" "401" "Trade GET with forged token" "$INVALID_TOKEN_RESPONSE"

echo
echo ">>> TEST 6: GET /api/v1/accounts/$ACCOUNT_ID with a real token"

ACCOUNT_STATUS="$(curl -sS \
    -o "$TMP_DIR/account.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID" \
    -H "Authorization: Bearer $ACCESS_TOKEN")"
ACCOUNT_RESPONSE="$(cat "$TMP_DIR/account.json")"

assert_status "$ACCOUNT_STATUS" "200" "Trade account lookup with real token" "$ACCOUNT_RESPONSE"
assert_body_contains "$TMP_DIR/account.json" '"id"[[:space:]]*:[[:space:]]*'"$ACCOUNT_ID" "Trade account response contains account id $ACCOUNT_ID"

echo
echo ">>> TEST 7: GET /api/v1/accounts/$ACCOUNT_ID/balance with a real token"

BALANCE_STATUS="$(curl -sS \
    -o "$TMP_DIR/balance.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID/balance" \
    -H "Authorization: Bearer $ACCESS_TOKEN")"
BALANCE_RESPONSE="$(cat "$TMP_DIR/balance.json")"

assert_status "$BALANCE_STATUS" "200" "Trade balance lookup with real token" "$BALANCE_RESPONSE"
assert_body_contains "$TMP_DIR/balance.json" '"accountId"[[:space:]]*:[[:space:]]*'"$ACCOUNT_ID" "Trade balance response contains account id $ACCOUNT_ID"

echo
echo ">>> TEST 8: GET /api/v1/accounts/$ACCOUNT_ID/positions with a real token"

POSITIONS_STATUS="$(curl -sS \
    -o "$TMP_DIR/positions.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID/positions" \
    -H "Authorization: Bearer $ACCESS_TOKEN")"
POSITIONS_RESPONSE="$(cat "$TMP_DIR/positions.json")"

assert_status "$POSITIONS_STATUS" "200" "Trade positions lookup with real token" "$POSITIONS_RESPONSE"

echo
echo ">>> TEST 9: GET /api/v1/accounts/$ACCOUNT_ID/orders with a real token"

ORDERS_STATUS="$(curl -sS \
    -o "$TMP_DIR/orders.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID/orders" \
    -H "Authorization: Bearer $ACCESS_TOKEN")"
ORDERS_RESPONSE="$(cat "$TMP_DIR/orders.json")"

assert_status "$ORDERS_STATUS" "200" "Trade orders lookup with real token" "$ORDERS_RESPONSE"

echo
echo "=================================================="
echo " Summary"
echo "=================================================="
echo "Passed : $PASS_COUNT"
echo "Failed : $FAIL_COUNT"
echo "=================================================="

if [[ "$FAIL_COUNT" -eq 0 ]]; then
    echo "PASS: End-to-end Trade API GET auth test succeeded"
else
    echo "FAIL: End-to-end Trade API GET auth test failed"
    exit 1
fi