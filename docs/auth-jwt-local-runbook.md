# Local Runbook and Verification Report

## Document Purpose

This runbook explains how to:

1. Start the JWT token Node server.
2. Start the Trade API.
3. Generate JWTs with the same logic used in Postman pre-request scripts.
4. Verify auth and authorization acceptance criteria from PowerShell.
5. Troubleshoot common local issues quickly.

This is intended as a reusable reference for local testing and demo runs.

---

## Scope Covered

- Local JWT generation service under `services/jwt-token-server`
- Local API under `trade-api`
- PowerShell commands for happy path and negative auth tests
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
4. Ports available:
   - `3001` for JWT server
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
JWT token server listening on http://localhost:3001
```

Health check (from terminal B):

```powershell
Invoke-RestMethod "http://localhost:3001/health"
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

If startup is successful, keep this terminal running.

---

## Generate Token and Call API

Run in a new PowerShell terminal:

```powershell
$jwtBase = "http://localhost:3001"
$apiBase = "http://localhost:8081"

$token = (Invoke-RestMethod "$jwtBase/token?accountId=1&expOffsetSec=600&secret=trade-api-shared-secret&headerAlg=HS256&signAlg=HS256").token
$h = @{ Authorization = "Bearer $token" }

Invoke-RestMethod "$apiBase/api/v1/accounts/1" -Headers $h
Invoke-RestMethod "$apiBase/api/v1/accounts/1/balance" -Headers $h
Invoke-RestMethod "$apiBase/api/v1/accounts/1/positions" -Headers $h
Invoke-RestMethod "$apiBase/api/v1/accounts/1/orders" -Headers $h
```

---

## Full Verification Matrix (PowerShell)

### 1) Missing Header -> AUTH-401

```powershell
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1"
```

Expected: status `401`, body contains `{"errorCode":"AUTH-401","message":"Unauthorised"}`

### 2) Wrong Scheme -> AUTH-401

```powershell
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Basic abc" }
```

Expected: status `401`, body contains `AUTH-401`

### 3) Expired Token -> AUTH-401

```powershell
$expired = (Invoke-RestMethod "http://localhost:3001/token?accountId=1&expOffsetSec=-60&secret=trade-api-shared-secret&headerAlg=HS256&signAlg=HS256").token
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $expired" }
```

Expected: status `401`, body contains `AUTH-401`

### 4) Forged Signature -> AUTH-401

```powershell
$forged = (Invoke-RestMethod "http://localhost:3001/token?accountId=1&expOffsetSec=600&secret=wrong-secret&headerAlg=HS256&signAlg=HS256").token
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $forged" }
```

Expected: status `401`, body contains `AUTH-401`

### 5) Wrong Algorithm -> AUTH-401

```powershell
$wrongAlg = (Invoke-RestMethod "http://localhost:3001/token?accountId=1&expOffsetSec=600&secret=trade-api-shared-secret&headerAlg=HS512&signAlg=HS256").token
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $wrongAlg" }
```

Expected: status `401`, body contains `AUTH-401`

### 6) Account Mismatch -> ACC-403

```powershell
$mismatch = (Invoke-RestMethod "http://localhost:3001/token?accountId=2&expOffsetSec=600&secret=trade-api-shared-secret&headerAlg=HS256&signAlg=HS256").token
Invoke-WebRequest "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $mismatch" }
```

Expected: status `403`, body contains `{"errorCode":"ACC-403","message":"Account not active"}`

---

## One-Block Smoke Test Script

Use this script to generate a token and test all account routes quickly:

```powershell
$jwtBase = "http://localhost:3001"
$apiBase = "http://localhost:8081"

$token = (Invoke-RestMethod "$jwtBase/token?accountId=1&expOffsetSec=600&secret=trade-api-shared-secret&headerAlg=HS256&signAlg=HS256").token
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

---

## Troubleshooting Guide

### Error: Unable to connect to the remote server

Cause:
- JWT server is not running.
- `$jwtBase` not set correctly.

Fix:

```powershell
Invoke-RestMethod "http://localhost:3001/health"
```

If it fails, restart JWT server from `services/jwt-token-server`.

### Error: EADDRINUSE: address already in use :::3001

Cause:
- Another process is already bound to port `3001`.

Fix:

```powershell
Get-NetTCPConnection -LocalPort 3001 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
Stop-Process -Id <PID> -Force
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
- Test request missing authenticated account request attribute.

Fix:
- Include `requestAttr("authenticatedAccountId", ...)` in MVC tests where auth filter is bypassed.

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

---

## Report Notes

- JWT generation logic in Node server is equivalent to the Postman pre-request script logic (base64url + HMAC + JWT header/payload/signature assembly).
- Local verification confirms acceptance-criteria behavior can be validated entirely via PowerShell without Postman.

---

## Change Log

- 2026-09-10: Initial runbook and verification report created.
