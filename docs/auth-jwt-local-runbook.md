# Local Runbook and Verification Report

## Document Purpose

This runbook explains how to:

1. Start the JWT token Node server.
2. Start the Trade API.
3. Generate JWTs with the same logic used in Postman pre-request scripts.
4. Verify auth and authorization acceptance criteria from PowerShell or curl.
5. Troubleshoot common local issues quickly.

This is intended as a reusable reference for local testing and demo runs.

---

## Scope Covered

- Local JWT generation service under `services/jwt-token-server`
- Local API under `trade-api`
- PowerShell and curl (macOS / Linux / Git Bash) commands for happy path and negative auth tests
- Validation of:
  - `AUTH-401` for missing/wrong/expired/forged/wrong-alg tokens
  - `ACC-403` for account mismatch

---

## Preconditions

1. Node.js installed (tested with Node.js v24.x).
2. Java and Maven wrapper available for Spring Boot app startup.
3. Two terminals available:
   - One for JWT server
   - One for API and test commands
4. For the curl examples: a bash-compatible shell with `curl` (macOS, Linux, or Git Bash / WSL on Windows).
5. Ports available:
   - `8082` for JWT server
   - `8081` for Trade API

---

## Directory Layout Reference

- JWT server: `services/jwt-token-server`
- API module: `trade-api`
- JWT server docs: `services/jwt-token-server/README.md`

---

## Startup Flow

### Step 1: Start JWT Token Server

Open terminal A:

```powershell
cd C:\Users\Administrator\Desktop\feature-spring-boot\chennai-capstone-SE5-team5\services\jwt-token-server
npm start
```

Expected log:

```text
JWT token server listening on http://localhost:8082
```

Health check (from terminal B):

```powershell
Invoke-RestMethod "http://localhost:8082/health"
```

curl:

```bash
curl -s http://localhost:8082/health; echo
```

Expected response:

```json
{"status":"ok"}
```

### Step 2: Start Trade API

Open terminal B:

```powershell
cd C:\Users\Administrator\Desktop\feature-spring-boot\chennai-capstone-SE5-team5\trade-api
.\mvnw.cmd spring-boot:run
```

bash (from the repository root):

```bash
cd trade-api
./mvnw spring-boot:run
```

If startup is successful, keep this terminal running.

---

## Generate Token and Call API

Run in a new PowerShell terminal:

```powershell
$jwtBase = "http://localhost:8082"
$apiBase = "http://localhost:8081"

$token = (Invoke-RestMethod "$jwtBase/token?accountId=1&expOffsetSec=600&secret=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001&headerAlg=HS256&signAlg=HS256").token
$h = @{ Authorization = "Bearer $token" }

Invoke-RestMethod "$apiBase/api/v1/accounts/1" -Headers $h
Invoke-RestMethod "$apiBase/api/v1/accounts/1/balance" -Headers $h
Invoke-RestMethod "$apiBase/api/v1/accounts/1/positions" -Headers $h
Invoke-RestMethod "$apiBase/api/v1/accounts/1/orders" -Headers $h
```

### curl setup (run once per terminal)

The curl examples below all reuse these variables and the `get_token` helper. `get_token` calls the JWT server and prints just the token string, so it needs no extra tools such as `jq`.

```bash
JWT_BASE=http://localhost:8082
API_BASE=http://localhost:8081
SECRET=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001

get_token() {
  curl -s "$JWT_BASE/token?$1" | sed -E 's/.*"token":"([^"]+)".*/\1/'
}
```

Generate a token and call the account routes:

```bash
TOKEN=$(get_token "accountId=1&expOffsetSec=600&secret=$SECRET&headerAlg=HS256&signAlg=HS256")

curl -s "$API_BASE/api/v1/accounts/1"           -H "Authorization: Bearer $TOKEN"; echo
curl -s "$API_BASE/api/v1/accounts/1/balance"   -H "Authorization: Bearer $TOKEN"; echo
curl -s "$API_BASE/api/v1/accounts/1/positions" -H "Authorization: Bearer $TOKEN"; echo
curl -s "$API_BASE/api/v1/accounts/1/orders"    -H "Authorization: Bearer $TOKEN"; echo
```

---

## Full Verification Matrix (PowerShell and curl)

The curl commands add `-w "\nHTTP %{http_code}\n"` so the status code is printed after the response body.

### 1) Missing Header -> AUTH-401

```powershell
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1"
```

```bash
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/1"
```

Expected: status `401`, body contains `{"errorCode":"AUTH-401","message":"Unauthorised"}`

### 2) Wrong Scheme -> AUTH-401

```powershell
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Basic abc" }
```

```bash
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/1" -H "Authorization: Basic abc"
```

Expected: status `401`, body contains `AUTH-401`

### 3) Expired Token -> AUTH-401

```powershell
$expired = (Invoke-RestMethod "http://localhost:8082/token?accountId=1&expOffsetSec=-60&secret=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001&headerAlg=HS256&signAlg=HS256").token
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $expired" }
```

```bash
EXPIRED=$(get_token "accountId=1&expOffsetSec=-60&secret=$SECRET&headerAlg=HS256&signAlg=HS256")
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/1" -H "Authorization: Bearer $EXPIRED"
```

Expected: status `401`, body contains `AUTH-401`

### 4) Forged Signature -> AUTH-401

```powershell
$forged = (Invoke-RestMethod "http://localhost:8082/token?accountId=1&expOffsetSec=600&secret=wrong-secret&headerAlg=HS256&signAlg=HS256").token
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $forged" }
```

```bash
FORGED=$(get_token "accountId=1&expOffsetSec=600&secret=wrong-secret&headerAlg=HS256&signAlg=HS256")
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/1" -H "Authorization: Bearer $FORGED"
```

Expected: status `401`, body contains `AUTH-401`

### 5) Wrong Algorithm -> AUTH-401

```powershell
$wrongAlg = (Invoke-RestMethod "http://localhost:8082/token?accountId=1&expOffsetSec=600&secret=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001&headerAlg=HS512&signAlg=HS256").token
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $wrongAlg" }
```

```bash
WRONG_ALG=$(get_token "accountId=1&expOffsetSec=600&secret=$SECRET&headerAlg=HS512&signAlg=HS256")
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/1" -H "Authorization: Bearer $WRONG_ALG"
```

Expected: status `401`, body contains `AUTH-401`

### 6) Account Mismatch -> ACC-403

```powershell
$mismatch = (Invoke-RestMethod "http://localhost:8082/token?accountId=2&expOffsetSec=600&secret=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001&headerAlg=HS256&signAlg=HS256").token
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $mismatch" }
```

```bash
MISMATCH=$(get_token "accountId=2&expOffsetSec=600&secret=$SECRET&headerAlg=HS256&signAlg=HS256")
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/1" -H "Authorization: Bearer $MISMATCH"
```

Expected: status `403`, body contains `{"errorCode":"ACC-403","message":"Account not active"}`

---

## One-Block Smoke Test Script

Use this script to generate a token and test all account routes quickly:

```powershell
$jwtBase = "http://localhost:8082"
$apiBase = "http://localhost:8081"

$token = (Invoke-RestMethod "$jwtBase/token?accountId=1&expOffsetSec=600&secret=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001&headerAlg=HS256&signAlg=HS256").token
$h = @{ Authorization = "Bearer $token" }

"GET /accounts/1"
Invoke-RestMethod "$apiBase/api/v1/accounts/1" -Headers $h

"GET /accounts/1/balance"
Invoke-RestMethod "$apiBase/api/v1/accounts/1/balance" -Headers $h

"GET /accounts/1/positions"
Invoke-RestMethod "$apiBase/api/v1/accounts/1/positions" -Headers $h

"GET /accounts/1/orders"
Invoke-RestMethod "$apiBase/api/v1/accounts/1/orders" -Headers $h
```

curl equivalent (self-contained, paste as one block):

```bash
JWT_BASE=http://localhost:8082
API_BASE=http://localhost:8081
SECRET=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001

TOKEN=$(curl -s "$JWT_BASE/token?accountId=1&expOffsetSec=600&secret=$SECRET&headerAlg=HS256&signAlg=HS256" \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')

for route in "" /balance /positions /orders; do
  echo "GET /accounts/1$route"
  curl -s -w "\nHTTP %{http_code}\n\n" "$API_BASE/api/v1/accounts/1$route" -H "Authorization: Bearer $TOKEN"
done
```

---

## Troubleshooting Guide

### Error: Unable to connect to the remote server

Cause:
- JWT server is not running.
- `$jwtBase` not set correctly.

Fix:

```powershell
Invoke-RestMethod "http://localhost:8082/health"
```

```bash
curl -s http://localhost:8082/health; echo
```

If it fails, restart JWT server from `services/jwt-token-server`.

### Error: EADDRINUSE: address already in use :::8082

Cause:
- Another process is already bound to port `8082`.

Fix:

```powershell
Get-NetTCPConnection -LocalPort 8082 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
Stop-Process -Id <PID> -Force
```

macOS / Linux:

```bash
lsof -iTCP:8082 -sTCP:LISTEN
kill <PID>
```

If using PowerShell job:

```powershell
Get-Job -Name jwt-token-server
Stop-Job -Name jwt-token-server
Remove-Job -Name jwt-token-server
```

### Error: HTTP 500 from account endpoints

Cause:
- API runtime selected data-backed service path that is not fully configured for local DB/mappers.

Fix:
- Ensure latest service bean selection changes are present.
- Restart Trade API after pulling latest changes.

### Error: AUTH-401 unexpectedly on controller tests

Cause:
- The MockMvc request carries no authenticated JWT. Authentication is handled by Spring Security, and request attributes are no longer read.

Fix:
- Import `SecurityConfig` and `AuthWebMvcConfig` in the `@WebMvcTest`, and authenticate the request with Spring Security's test support:
  `.with(jwt().jwt(token -> token.claim("accountId", 101L)))`

### Error: Trade API fails to start with "auth.jwt.shared-secret (JWT_SECRET) must be ..."

Cause:
- `JWT_SECRET` is missing, or shorter than 32 bytes (48 for `HS384`, 64 for `HS512`).

Fix:
- Export a long enough secret before starting, and give the JWT token server the same value:

```powershell
$env:JWT_SECRET = "<a random string of at least 32 characters>"
```

```bash
export JWT_SECRET="<a random string of at least 32 characters>"
```

- For `docker compose --profile platform up`, set `JWT_SECRET` in a `.env` file at the repository root; compose refuses to start without it.

---

## Recommended Daily Workflow

1. Start JWT server.
2. Start Trade API.
3. Run smoke token generation and account route checks.
4. Run negative auth checks for `AUTH-401` and `ACC-403`.
5. Run API tests:

```powershell
cd C:\Users\Administrator\Desktop\feature-spring-boot\chennai-capstone-SE5-team5\trade-api
.\mvnw.cmd -q test
```

```bash
cd trade-api
./mvnw -q test
```

---

## Report Notes

- JWT generation logic in Node server is equivalent to the Postman pre-request script logic (base64url + HMAC + JWT header/payload/signature assembly).
- Local verification confirms acceptance-criteria behavior can be validated entirely via PowerShell or curl without Postman.

---

## Change Log

- 2026-09-10: Initial runbook and verification report created.
- 2026-09-14: Moved JWT verification to Spring Security (resource server). The shared secret must now be at least 32 bytes, so the local dev secret changed; update Postman pre-request scripts to match. JWT token server moved from port 3001 to 8082. `DELETE /api/v1/orders/{id}` now returns `ACC-403` for another account's order and 404 for an unknown order.
- 2026-09-14: Added curl (bash) equivalents for every PowerShell command.
