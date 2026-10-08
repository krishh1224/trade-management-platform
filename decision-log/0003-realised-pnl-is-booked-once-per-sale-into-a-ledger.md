# 0403 Realised PnL is booked once per sale into a ledger

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |

## Context

Realised PnL must remain stable and auditable, including for historical sales and replays. Current holdings and trade events alone do not always contain enough information to reconstruct average cost at sale time, especially when positions are fully sold. Migration V15 introduced `realised_pnl_ledger` keyed by `order_id` with idempotent insert semantics.

## Options considered

| Option | For | Against |
|---|---|---|
| Compute realised PnL on-demand from current state only | No new persistence | Incorrect when historical context is gone |
| Derive only from streaming events | Event-driven model | Event payload does not carry all cost basis details needed |
| Persist one ledger row per sale with idempotent booking | Stable and replay-safe accounting | Adds a write path and replay logic complexity |

## Decision

Use a dedicated realised PnL ledger and book each sale once (`ON CONFLICT DO NOTHING`) by replaying order history where needed. The deciding factor was correctness across restarts, replays, and full-sale scenarios.

## Consequences

- Realised figures stop drifting with current market state.
- Cost: replay logic must stay aligned with executor/order arithmetic and rounding.
- Cost: portfolio is no longer purely read-only.
