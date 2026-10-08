# 0701 Advisor runs in-process with read-only data access and bounded model-call timeouts

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |

## Context

Advisor functionality was merged into the Trade REST API process and serves `/api/v1/advisor/chat`. The module uses read-only tools and repository patterns; Gemini calls can stall and therefore compete for shared request threads. Advisor failure semantics are mapped to advisor-specific error codes instead of leaking generic trading route failures.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep advisor as standalone service | Better runtime isolation | Higher deployment complexity and integration surface |
| Run advisor in-process with default client behavior | Simple merge | Unbounded waits and higher risk to shared runtime |
| Run advisor in-process with read-only boundaries and explicit timeout/retry policy | Lower deployment overhead with bounded risk | Still shares thread and process resources with trading API |

## Decision

Run advisor in-process for now, but enforce read-only access boundaries and hard request timeout/retry behavior for model calls. The deciding factor was delivery simplicity with controlled runtime risk.

## Consequences

- Advisor ships with the main service and reuses existing auth/account boundaries.
- Cost: high advisor load can still affect shared process capacity.
- Follow-up: split advisor back out if traffic or SLO isolation requires dedicated runtime.
