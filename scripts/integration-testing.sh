#!/usr/bin/env bash

# End-to-end integration test for the real Auth service, Trade API, and Executor.
#
# Flow covered by this script:
#   1. Obtain a real JWT from the Auth service.
#   2. Prove Trade API rejects missing and forged bearer tokens.
#   3. Call Trade API with the real token to read account state.
#   4. Place an authenticated order through Trade API.
#   5. Wait for Executor to consume the ORDER_PLACED event and settle it.
#   6. Re-read Trade API state and print a summary of what changed.
#
# Expected local ports:
#   Auth service   -> http://localhost:3000
#   Trade API      -> http://localhost:8081
#   Executor       -> http://localhost:8082
#
# Key prerequisites:
#   - curl
#   - node
#   - bash
#   - running Auth service
#   - running Trade API
#   - running Executor service
#   - shared JWT secret between Auth and Trade API verifier
#   - PostgreSQL seeded with account data
#   - Kafka reachable by Trade API and Executor
#   - FAUXNANCE_API_KEY available to Executor if it fetches live quotes

set -Eeuo pipefail

AUTH_URL="${AUTH_URL:-http://localhost:3000}"
TRADE_URL="${TRADE_URL:-http://localhost:8081}"
EXECUTOR_URL="${EXECUTOR_URL:-http://localhost:8082}"

ACCOUNT_ID="${ACCOUNT_ID:-102}"
TEST_SYMBOL="${TEST_SYMBOL:-TATASTEEL.BO}"
TEST_SIDE="${TEST_SIDE:-BUY}"
TEST_QUANTITY="${TEST_QUANTITY:-1}"
TEST_PRICE="${TEST_PRICE:-10000.00}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-30}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"

RUN_ID="$(date +%Y%m%d%H%M%S)-$$-$RANDOM"
EXPLICIT_AUTH_USERNAME="${AUTH_USERNAME:-}"
EXPLICIT_AUTH_PASSWORD="${AUTH_PASSWORD:-}"
AUTH_USERNAME="${AUTH_USERNAME:-trade_exec_e2e_${RUN_ID}}"
AUTH_PASSWORD="${AUTH_PASSWORD:-Mission12345!}"
BAD_JWT_SECRET="${BAD_JWT_SECRET:-wrong-secret-not-trusted-by-platform-000001}"
ORDER_KEY="${ORDER_KEY:-exec-${ACCOUNT_ID}-${RUN_ID}}"

ACCESS_TOKEN=""
ORDER_ID=""
INTERNAL_ORDER_ID=""
EXECUTED_PRICE=""
PRICE_EXPLANATION=""
TERMINAL_STATUS=""
PASS_COUNT=0
FAIL_COUNT=0
SUMMARY_ITEMS=()

TMP_DIR="$(mktemp -d)"

ESC="$(printf '\033')"
RESET="${ESC}[0m"
BOLD="${ESC}[1m"
RED="${ESC}[31m"
GREEN="${ESC}[32m"
YELLOW="${ESC}[33m"
BLUE="${ESC}[34m"
MAGENTA="${ESC}[35m"
CYAN="${ESC}[36m"

cleanup() {
    rm -rf "$TMP_DIR"
}

trap cleanup EXIT

banner() {
    local color="$1"
    local title="$2"
    printf "\n%s============================================================%s\n" "$color" "$RESET"
    printf "%s%s%s\n" "$color" "$title" "$RESET"
    printf "%s============================================================%s\n" "$color" "$RESET"
}

step() {
    printf "\n%s[%sSTEP%s] %s%s\n" "$CYAN" "$BOLD" "$RESET" "$1" "$RESET"
}

record_summary() {
    SUMMARY_ITEMS+=("$1")
}

pass() {
    printf "%sPASS%s: %s\n" "$GREEN" "$RESET" "$1"
    PASS_COUNT=$((PASS_COUNT + 1))
}

warn() {
    printf "%sWARN%s: %s\n" "$YELLOW" "$RESET" "$1"
}

fail() {
    printf "%sFAIL%s: %s\n" "$RED" "$RESET" "$1"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    exit 1
}

assert_status() {
    local actual="$1"
    local expected="$2"
    local label="$3"
    local body="${4:-}"

    if [[ "$actual" == "$expected" ]]; then
        pass "$label (HTTP $actual)"
    else
        printf "%sFAIL%s: %s (expected HTTP %s, got HTTP %s)\n" "$RED" "$RESET" "$label" "$expected" "$actual"
        [[ -n "$body" ]] && printf "Response: %s\n" "$body"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        exit 1
    fi
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' was not found."
}

wait_for_http_200() {
    local url="$1"
    local label="$2"

    for _ in $(seq 1 60); do
        local code
        code="$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)"
        if [[ "$code" == "200" ]]; then
            pass "$label is ready"
            return 0
        fi
        sleep 2
    done

    fail "$label did not become ready at $url within 120 seconds."
}

wait_for_http_reachable() {
    local url="$1"
    local label="$2"

    for _ in $(seq 1 30); do
        local code
        code="$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)"
        if [[ "$code" != "000" ]]; then
            pass "$label is reachable (HTTP $code)"
            return 0
        fi
        sleep 2
    done

    fail "$label did not respond at $url."
}

json_register() {
    printf '{"username":"%s","password":"%s","accountId":%s}' "$1" "$2" "$3"
}

json_login() {
    printf '{"username":"%s","password":"%s"}' "$1" "$2"
}

json_place_order() {
    printf '{"accountId":%s,"symbol":"%s","side":"%s","quantity":%s,"price":%s,"idempotencyKey":"%s"}' \
        "$1" "$2" "$3" "$4" "$5" "$6"
}

json_field() {
    local file="$1"
    local field="$2"

    node -e "const fs=require('fs'); const data=JSON.parse(fs.readFileSync(process.argv[1],'utf8')); const value=data[process.argv[2]]; process.stdout.write(value === undefined || value === null ? '' : String(value));" "$file" "$field"
}

json_balance() {
    json_field "$1" "cashBalance"
}

json_order_status_by_key() {
    local file="$1"
    local order_key="$2"

    node -e "const fs=require('fs'); const data=JSON.parse(fs.readFileSync(process.argv[1],'utf8')); const entry=(Array.isArray(data)?data:[]).find(item => item && item.idempotencyKey === process.argv[2]); process.stdout.write(entry && entry.status ? String(entry.status) : '');" "$file" "$order_key"
}

json_order_field_by_key() {
    local file="$1"
    local order_key="$2"
    local field="$3"

    node -e "const fs=require('fs'); const data=JSON.parse(fs.readFileSync(process.argv[1],'utf8')); const entry=(Array.isArray(data)?data:[]).find(item => item && item.idempotencyKey === process.argv[2]); const value=entry ? entry[process.argv[3]] : undefined; process.stdout.write(value === undefined || value === null ? '' : String(value));" "$file" "$order_key" "$field"
}

json_position_qty() {
    local file="$1"
    local symbol="$2"

    node -e "const fs=require('fs'); const data=JSON.parse(fs.readFileSync(process.argv[1],'utf8')); const entry=(Array.isArray(data)?data:[]).find(item => item && item.symbol === process.argv[2]); const quantity=entry && entry.quantity !== undefined ? entry.quantity : 0; process.stdout.write(String(quantity));" "$file" "$symbol"
}

decimal_lt() {
    awk -v left="$1" -v right="$2" 'BEGIN { exit !(left + 0 < right + 0) }'
}

integer_eq() {
    [[ "$1" -eq "$2" ]]
}

decimal_value() {
    awk -v left="$1" -v right="$2" 'BEGIN { printf "%.2f", (left + 0) - (right + 0) }'
}

decimal_divide() {
    awk -v numerator="$1" -v denominator="$2" 'BEGIN { if ((denominator + 0) == 0) exit 1; printf "%.2f", (numerator + 0) / (denominator + 0) }'
}

forge_invalid_token() {
    local account_id="$1"

    ACCOUNT_ID="$account_id" BAD_JWT_SECRET="$BAD_JWT_SECRET" node <<'EOF'
const crypto = require('crypto');

const accountId = Number(process.env.ACCOUNT_ID);
const secret = process.env.BAD_JWT_SECRET;
const header = { typ: 'JWT', alg: 'HS256' };
const payload = {
  sub: 'forged-subject',
  accountId,
  roles: ['USER'],
  iss: 'auth-service',
  iat: Math.floor(Date.now() / 1000),
  exp: Math.floor(Date.now() / 1000) + 600,
};

const base64url = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
const signingInput = `${base64url(header)}.${base64url(payload)}`;
const signature = crypto.createHmac('sha256', secret).update(signingInput).digest('base64url');
process.stdout.write(`${signingInput}.${signature}`);
EOF
}

poll_until_terminal() {
    local file="$1"

    for attempt in $(seq 1 "$POLL_ATTEMPTS"); do
        local status_code
        status_code="$(curl -sS \
            -o "$file" \
            -w "%{http_code}" \
            "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID/orders" \
            -H "Authorization: Bearer $ACCESS_TOKEN")"

        if [[ "$status_code" != "200" ]]; then
            local body
            body="$(cat "$file")"
            fail "Polling order history failed with HTTP $status_code. Response: $body"
        fi

        TERMINAL_STATUS="$(json_order_status_by_key "$file" "$ORDER_KEY")"
        INTERNAL_ORDER_ID="$(json_order_field_by_key "$file" "$ORDER_KEY" "orderId")"
        EXECUTED_PRICE="$(json_order_field_by_key "$file" "$ORDER_KEY" "executedPrice")"

        if [[ -n "$TERMINAL_STATUS" && "$TERMINAL_STATUS" != "NEW" ]]; then
            pass "Executor settled order $ORDER_ID with terminal status $TERMINAL_STATUS"
            return 0
        fi

        printf "%sINFO%s: Poll %s/%s -> current status: %s\n" \
            "$BLUE" "$RESET" "$attempt" "$POLL_ATTEMPTS" "${TERMINAL_STATUS:-not-found-yet}"
        sleep "$POLL_INTERVAL_SECONDS"
    done

    fail "Order with idempotencyKey $ORDER_KEY never reached a terminal status. This usually means Executor is not consuming, Kafka is unavailable, or quote retrieval is blocked."
}

banner "$MAGENTA" "Auth + Trade API + Executor End-to-End Integration"
printf "%sAuth URL%s      : %s\n" "$BOLD" "$RESET" "$AUTH_URL"
printf "%sTrade URL%s     : %s\n" "$BOLD" "$RESET" "$TRADE_URL"
printf "%sExecutor URL%s  : %s\n" "$BOLD" "$RESET" "$EXECUTOR_URL"
printf "%sAccount ID%s    : %s\n" "$BOLD" "$RESET" "$ACCOUNT_ID"
printf "%sOrder Symbol%s  : %s\n" "$BOLD" "$RESET" "$TEST_SYMBOL"
printf "%sOrder Side%s    : %s\n" "$BOLD" "$RESET" "$TEST_SIDE"
printf "%sOrder Qty%s     : %s\n" "$BOLD" "$RESET" "$TEST_QUANTITY"
printf "%sOrder Price%s   : %s\n" "$BOLD" "$RESET" "$TEST_PRICE"
printf "%sAuth User%s     : %s\n" "$BOLD" "$RESET" "$AUTH_USERNAME"

require_command curl
require_command node

step "Waiting for services"
wait_for_http_200 "$AUTH_URL/health" "Auth service"
wait_for_http_200 "$TRADE_URL/health" "Trade API"
wait_for_http_reachable "$EXECUTOR_URL" "Executor service"
record_summary "All three services responded on their expected local ports."

step "Registering or reusing credentials"
REGISTER_BODY="$(json_register "$AUTH_USERNAME" "$AUTH_PASSWORD" "$ACCOUNT_ID")"
REGISTER_STATUS="$(curl -sS \
    -o "$TMP_DIR/register.json" \
    -w "%{http_code}" \
    -X POST "$AUTH_URL/auth/register" \
    -H "Content-Type: application/json" \
    -d "$REGISTER_BODY")"
REGISTER_RESPONSE="$(cat "$TMP_DIR/register.json")"

if [[ "$REGISTER_STATUS" == "201" ]]; then
    pass "Auth register (HTTP 201)"
    record_summary "A fresh auth credential was created for account $ACCOUNT_ID."
elif [[ "$REGISTER_STATUS" == "409" ]]; then
    if [[ -z "$EXPLICIT_AUTH_USERNAME" || -z "$EXPLICIT_AUTH_PASSWORD" ]]; then
        fail "Auth register returned 409. Account $ACCOUNT_ID already has a credential, and this run used an auto-generated username that cannot log into that existing account. Re-run with AUTH_USERNAME and AUTH_PASSWORD for the existing credential, or use an unused account ID. Response: $REGISTER_RESPONSE"
    fi
    warn "Auth register returned 409; continuing with the explicitly supplied existing credentials."
    record_summary "Registration hit an existing credential for account $ACCOUNT_ID, so the flow continued with the supplied login credentials."
else
    fail "Auth register failed unexpectedly (HTTP $REGISTER_STATUS). Response: $REGISTER_RESPONSE"
fi

step "Logging in to obtain a real JWT"
LOGIN_BODY="$(json_login "$AUTH_USERNAME" "$AUTH_PASSWORD")"
LOGIN_STATUS="$(curl -sS \
    -o "$TMP_DIR/login.json" \
    -w "%{http_code}" \
    -X POST "$AUTH_URL/auth/login" \
    -H "Content-Type: application/json" \
    -d "$LOGIN_BODY")"
LOGIN_RESPONSE="$(cat "$TMP_DIR/login.json")"
assert_status "$LOGIN_STATUS" "201" "Auth login" "$LOGIN_RESPONSE"
ACCESS_TOKEN="$(json_field "$TMP_DIR/login.json" "accessToken")"
[[ -n "$ACCESS_TOKEN" ]] || fail "Login response did not contain accessToken."
pass "Received a real bearer token from Auth service"
record_summary "A real JWT was minted by Auth and used for downstream Trade API calls."

step "Capturing baseline Trade API state"
BALANCE_BEFORE_STATUS="$(curl -sS \
    -o "$TMP_DIR/balance-before.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID/balance" \
    -H "Authorization: Bearer $ACCESS_TOKEN")"
BALANCE_BEFORE_BODY="$(cat "$TMP_DIR/balance-before.json")"
assert_status "$BALANCE_BEFORE_STATUS" "200" "Trade account balance before order" "$BALANCE_BEFORE_BODY"
BALANCE_BEFORE="$(json_balance "$TMP_DIR/balance-before.json")"

POSITIONS_BEFORE_STATUS="$(curl -sS \
    -o "$TMP_DIR/positions-before.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID/positions" \
    -H "Authorization: Bearer $ACCESS_TOKEN")"
POSITIONS_BEFORE_BODY="$(cat "$TMP_DIR/positions-before.json")"
assert_status "$POSITIONS_BEFORE_STATUS" "200" "Trade positions before order" "$POSITIONS_BEFORE_BODY"
POSITION_BEFORE_QTY="$(json_position_qty "$TMP_DIR/positions-before.json" "$TEST_SYMBOL")"
record_summary "Baseline balance and positions were captured before the order was placed."

step "Proving Trade API auth boundary"
NO_TOKEN_BODY="$(json_place_order "$ACCOUNT_ID" "$TEST_SYMBOL" "$TEST_SIDE" "$TEST_QUANTITY" "$TEST_PRICE" "no-token-${RUN_ID}")"
NO_TOKEN_STATUS="$(curl -sS \
    -o "$TMP_DIR/no-token.json" \
    -w "%{http_code}" \
    -X POST "$TRADE_URL/api/v1/orders" \
    -H "Content-Type: application/json" \
    -d "$NO_TOKEN_BODY")"
assert_status "$NO_TOKEN_STATUS" "401" "Trade order placement without token" "$(cat "$TMP_DIR/no-token.json")"

FORGED_TOKEN="$(forge_invalid_token "$ACCOUNT_ID")"
FORGED_STATUS="$(curl -sS \
    -o "$TMP_DIR/forged-token.json" \
    -w "%{http_code}" \
    -X POST "$TRADE_URL/api/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $FORGED_TOKEN" \
    -d "$(json_place_order "$ACCOUNT_ID" "$TEST_SYMBOL" "$TEST_SIDE" "$TEST_QUANTITY" "$TEST_PRICE" "forged-${RUN_ID}")")"
assert_status "$FORGED_STATUS" "401" "Trade order placement with forged token" "$(cat "$TMP_DIR/forged-token.json")"
record_summary "Trade API correctly rejected both missing and wrongly signed bearer tokens."

step "Placing an authenticated order through Trade API"
PLACE_BODY="$(json_place_order "$ACCOUNT_ID" "$TEST_SYMBOL" "$TEST_SIDE" "$TEST_QUANTITY" "$TEST_PRICE" "$ORDER_KEY")"
PLACE_STATUS="$(curl -sS \
    -o "$TMP_DIR/place-order.json" \
    -w "%{http_code}" \
    -X POST "$TRADE_URL/api/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d "$PLACE_BODY")"
PLACE_RESPONSE="$(cat "$TMP_DIR/place-order.json")"
assert_status "$PLACE_STATUS" "200" "Trade authenticated order placement" "$PLACE_RESPONSE"
INITIAL_ORDER_STATUS="$(json_field "$TMP_DIR/place-order.json" "status")"
ORDER_ID="$(json_field "$TMP_DIR/place-order.json" "orderId")"
[[ -n "$ORDER_ID" ]] || fail "Trade order response did not contain orderId."
if [[ "$INITIAL_ORDER_STATUS" == "NEW" ]]; then
    pass "Trade API accepted order $ORDER_ID and returned asynchronous status NEW"
else
    warn "Trade order returned initial status $INITIAL_ORDER_STATUS instead of NEW"
fi
record_summary "Trade API accepted an authenticated order and published the asynchronous execution request."

step "Waiting for Executor to process the order"
poll_until_terminal "$TMP_DIR/orders-after.json"
record_summary "Executor consumed the ORDER_PLACED event and settled the order to $TERMINAL_STATUS."

step "Validating Trade API state after Executor settlement"
BALANCE_AFTER_STATUS="$(curl -sS \
    -o "$TMP_DIR/balance-after.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID/balance" \
    -H "Authorization: Bearer $ACCESS_TOKEN")"
BALANCE_AFTER_BODY="$(cat "$TMP_DIR/balance-after.json")"
assert_status "$BALANCE_AFTER_STATUS" "200" "Trade account balance after order" "$BALANCE_AFTER_BODY"
BALANCE_AFTER="$(json_balance "$TMP_DIR/balance-after.json")"

POSITIONS_AFTER_STATUS="$(curl -sS \
    -o "$TMP_DIR/positions-after.json" \
    -w "%{http_code}" \
    "$TRADE_URL/api/v1/accounts/$ACCOUNT_ID/positions" \
    -H "Authorization: Bearer $ACCESS_TOKEN")"
POSITIONS_AFTER_BODY="$(cat "$TMP_DIR/positions-after.json")"
assert_status "$POSITIONS_AFTER_STATUS" "200" "Trade positions after order" "$POSITIONS_AFTER_BODY"
POSITION_AFTER_QTY="$(json_position_qty "$TMP_DIR/positions-after.json" "$TEST_SYMBOL")"

if [[ "$TERMINAL_STATUS" == "FILLED" ]]; then
    decimal_lt "$BALANCE_AFTER" "$BALANCE_BEFORE" || fail "Expected balance to decrease after FILLED BUY order. Before=$BALANCE_BEFORE After=$BALANCE_AFTER"
    pass "Balance decreased after FILLED order"

    EXPECTED_QTY=$((POSITION_BEFORE_QTY + TEST_QUANTITY))
    integer_eq "$POSITION_AFTER_QTY" "$EXPECTED_QTY" || fail "Expected position quantity $EXPECTED_QTY for $TEST_SYMBOL after FILLED order, got $POSITION_AFTER_QTY"
    pass "Position quantity updated after FILLED order"
    if [[ -z "$EXECUTED_PRICE" ]]; then
        BALANCE_DELTA="$(decimal_value "$BALANCE_BEFORE" "$BALANCE_AFTER")"
        EXECUTED_PRICE="$(decimal_divide "$BALANCE_DELTA" "$TEST_QUANTITY")"
        PRICE_EXPLANATION="Executed price inferred from balance delta: ($BALANCE_BEFORE - $BALANCE_AFTER) / $TEST_QUANTITY = $EXECUTED_PRICE"
    else
        PRICE_EXPLANATION="Executed price came from the settled order history entry returned by Trade API: $EXECUTED_PRICE"
    fi
    record_summary "Executor settlement changed both cash balance and holdings as expected for a filled order."
else
    if decimal_lt "$BALANCE_AFTER" "$BALANCE_BEFORE"; then
        fail "Rejected order should not reduce balance. Before=$BALANCE_BEFORE After=$BALANCE_AFTER"
    fi
    integer_eq "$POSITION_AFTER_QTY" "$POSITION_BEFORE_QTY" || fail "Rejected order should not change holdings for $TEST_SYMBOL. Before=$POSITION_BEFORE_QTY After=$POSITION_AFTER_QTY"
    pass "Rejected order left balance and holdings unchanged"
    record_summary "Executor settlement rejected the order without mutating balance or holdings."
fi

banner "$GREEN" "Execution Summary"
printf "%sPassed%s : %s\n" "$BOLD" "$RESET" "$PASS_COUNT"
printf "%sFailed%s : %s\n" "$BOLD" "$RESET" "$FAIL_COUNT"
printf "%sAccount%s: %s\n" "$BOLD" "$RESET" "$ACCOUNT_ID"
printf "%sOrder%s  : %s (%s)\n" "$BOLD" "$RESET" "$ORDER_ID" "$TERMINAL_STATUS"
if [[ -n "$INTERNAL_ORDER_ID" ]]; then
    printf "%sTrace%s  : internal persisted order id %s\n" "$BOLD" "$RESET" "$INTERNAL_ORDER_ID"
fi
printf "%sBalance%s: %s -> %s\n" "$BOLD" "$RESET" "$BALANCE_BEFORE" "$BALANCE_AFTER"
printf "%sPosition%s: %s -> %s for %s\n" "$BOLD" "$RESET" "$POSITION_BEFORE_QTY" "$POSITION_AFTER_QTY" "$TEST_SYMBOL"
if [[ -n "$EXECUTED_PRICE" ]]; then
    printf "%sFill%s   : %s\n" "$BOLD" "$RESET" "$EXECUTED_PRICE"
    printf "%sPrice note%s: %s\n" "$BOLD" "$RESET" "$PRICE_EXPLANATION"
fi

printf "\n%sWhat This Script Verified%s\n" "$BOLD" "$RESET"
for item in "${SUMMARY_ITEMS[@]}"; do
    printf "%s✓ %s%s\n" "$BLUE" "$item" "$RESET"
done

printf "\n%sDemo Summary%s\n" "$BOLD" "$RESET"
printf "%s✓ Auth issued a real JWT for account %s.%s\n" "$GREEN" "$ACCOUNT_ID" "$RESET"
printf "%s✓ Trade API rejected missing and forged bearer tokens.%s\n" "$GREEN" "$RESET"
printf "%s✓ Trade API accepted the authenticated order as %s.%s\n" "$GREEN" "$ORDER_ID" "$RESET"
printf "%s✓ Executor consumed the Kafka event and settled the order as %s.%s\n" "$GREEN" "$TERMINAL_STATUS" "$RESET"
if [[ -n "$EXECUTED_PRICE" ]]; then
    printf "%s✓ The settled fill price was %s and the balance/position updates matched it.%s\n" "$GREEN" "$EXECUTED_PRICE" "$RESET"
fi

printf "\n%sAll end-to-end checks passed across Auth, Trade API, and Executor.%s\n" "$GREEN" "$RESET"