# JWT Token Server

Standalone Node server to generate JWTs for trade-api auth testing.

Comprehensive runbook and PowerShell verification report:

- `../../docs/auth-jwt-local-runbook.md`

## Run

```powershell
cd services/jwt-token-server
npm start
```

Server starts on `http://localhost:3001` by default.

## Generate a token (GET)

```powershell
curl "http://localhost:3001/token?accountId=1&expOffsetSec=600&secret=trade-api-shared-secret&headerAlg=HS256&signAlg=HS256"
```

## Generate a token (POST)

```powershell
curl -Method POST "http://localhost:3001/token" -ContentType "application/json" -Body '{"accountId":1,"expOffsetSec":600,"secret":"trade-api-shared-secret","headerAlg":"HS256","signAlg":"HS256"}'
```

## Use token against trade-api

```powershell
$tokenResponse = Invoke-RestMethod "http://localhost:3001/token?accountId=1&expOffsetSec=600&secret=trade-api-shared-secret&headerAlg=HS256&signAlg=HS256"
$token = $tokenResponse.token
Invoke-RestMethod "http://localhost:8081/api/v1/accounts/1" -Headers @{ Authorization = "Bearer $token" }
```

## Negative test examples

- Expired token: set `expOffsetSec=-60`
- Forged signature: use wrong `secret`
- Wrong algorithm in header: `headerAlg=HS512` with `signAlg=HS256`
- Account mismatch: token `accountId=2` for `/accounts/1`
