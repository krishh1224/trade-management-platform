# Auth Stub

This stub documents the JWT contract used by trade-api for local infrastructure and testing.

## Token Contract

- Header:
  - `typ`: `JWT`
  - `alg`: `HS256` (default, configurable in trade-api)
- Required payload claims:
  - `accountId` (number): positive whole-number account key that must match addressed account; decimals and strings are rejected
  - `exp` (number): expiration time as Unix epoch seconds; tokens without it are rejected

## Signing

- Shared signing secret:
  - Use environment variable `JWT_SECRET`
  - Must be at least 32 bytes for `HS256` (48 for `HS384`, 64 for `HS512`); trade-api refuses to start otherwise
  - The `platform` profile (docker-compose) has no default and refuses to start without it
  - The `local` profile falls back to a dev-only secret, `local-dev-only-trade-api-jwt-secret-not-for-production-use-000001`, which the JWT token server also uses by default
- Expected algorithm:
  - Use environment variable `AUTH_JWT_ALGORITHM`
  - Default is `HS256`; tokens signed with any other algorithm are rejected
- Clock skew:
  - Use environment variable `AUTH_JWT_CLOCK_SKEW` (for example `30s`)
  - Default is `0s`, so a token is rejected as soon as `exp` has passed

## Demo Users

Use demo account IDs that exist in seeded data in your environment.

- Demo user A: accountId `1`
- Demo user B: accountId `2`

These are placeholders for local runs. Replace or extend as needed when wiring your own stub issuer.

## Behaviour Expected By Trade API

- Any token failure (missing header, wrong scheme, malformed token, forged signature, expired token, wrong algorithm) must resolve to:
  - HTTP `401`
  - `{ "errorCode": "AUTH-401", "message": "Unauthorised" }`
- A valid token whose `accountId` does not match addressed account resolves to:
  - HTTP `403`
  - `{ "errorCode": "ACC-403", "message": "Account not active" }`

## Sprint 8 Note

The API verifies JWTs by token contract (claims + signature), not by direct coupling to this stub implementation.
Switching to real auth service should be a configuration change only (secret/key source, algorithm, issuer setup).
