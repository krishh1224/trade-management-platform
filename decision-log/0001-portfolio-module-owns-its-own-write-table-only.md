# 0401 Portfolio module writes only its own table and reads everything else

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |

## Context

Portfolio runs in the same Trade REST API process and database as order placement. A mistaken write from portfolio into `accounts`, `orders`, or holdings would corrupt trading state. The module now writes `realised_pnl_ledger` and reads `instruments`, `market_quote_snapshots`, and `orders`. The codebase already uses mapper-level ownership tests to enforce write boundaries.

## Options considered

| Option | For | Against |
|---|---|---|
| Keep portfolio read-only forever | Lowest risk of cross-table corruption | Cannot persist realised PnL reliably across reloads and replays |
| Let portfolio write any needed table directly | Fast implementation | Violates module boundaries and increases blast radius for mistakes |
| Permit writes only to portfolio-owned table(s), enforced by mapper tests | Supports required ledger persistence while preserving boundaries | Adds test maintenance and ownership documentation overhead |

## Decision

Portfolio is allowed to write only its owned table (`realised_pnl_ledger`) and must keep all other module tables read-only, enforced by mapper tests. The deciding factor was balancing correctness for realised PnL with containment of write risk.

## Consequences

- Realised PnL can be persisted without opening broad write access.
- Cost: ownership tests and README ownership lists must stay updated with schema changes.
- Cost: any future portfolio write feature must justify a new owned table instead of reusing order tables.
