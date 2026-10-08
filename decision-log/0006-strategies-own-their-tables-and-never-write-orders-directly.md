# 0601 Strategies own their tables and never write orders directly

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |

## Context

Automated strategies evaluate quote streams and can place real customer orders. They require local state (`strategies`, `strategy_executions`, `strategy_quotes`) but must not bypass order validations or mutate order tables directly. The platform already has an authoritative order route (`POST /api/v1/orders`) with existing checks and side effects.

## Options considered

| Option | For | Against |
|---|---|---|
| Let strategy engine insert/update orders via mappers | Lowest latency | Bypasses route-level validation and security controls |
| Store all strategy state outside trade DB | Strong isolation | High integration overhead and cross-store consistency complexity |
| Keep strategy-owned tables for strategy state, place orders only via order API route | Preserves order boundary and keeps strategy logic modular | Additional integration work and HTTP hop |

## Decision

Strategies own and write only strategy tables, and all actual order creation goes through the existing order route. The deciding factor was maintaining one authoritative order boundary.

## Consequences

- Automated and manual orders share validation, authorization, and event publication behavior.
- Cost: strategy module has more moving parts (state tables + route integration).
- Cost: local direct-write optimizations to order tables are intentionally disallowed.
