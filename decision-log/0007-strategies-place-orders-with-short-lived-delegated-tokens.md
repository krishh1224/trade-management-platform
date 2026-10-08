# 0602 Strategies place orders with short-lived delegated tokens

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |

## Context

Strategies need to place orders while users may be offline. Reusing user access or refresh tokens creates expiry and privilege problems. Calling order internals without tokenized boundary bypasses account-scoped enforcement. Existing implementation uses delegated strategy tokens (issuer/scope/TTL constrained) for route-level authorization.

## Options considered

| Option | For | Against |
|---|---|---|
| Reuse user access token | No new token implementation | Expires too quickly for long-running automation |
| Reuse user refresh token | Long-lived | Over-privileged credential with larger blast radius |
| Mint short-lived delegated strategy token per order | Least privilege and bounded lifetime for automation | Additional signing/verification config and complexity |

## Decision

Mint short-lived delegated tokens per strategy order and require them at the same `/api/v1/orders` boundary as user orders. The deciding factor was least-privilege automation with auditable route enforcement.

## Consequences

- Strategy execution can continue while user is signed out.
- Cost: token issuer, secret management, and claim verification become operational dependencies.
- Cost: incorrect claim config can break strategy order placement system-wide.
