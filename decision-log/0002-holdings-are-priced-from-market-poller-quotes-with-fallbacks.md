# 0402 Holdings are priced from market poller quotes with explicit stale fallbacks

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |

## Context

Portfolio summary and positions must answer even when upstream pricing is partial or temporarily unavailable. The module first uses quote cache and batched Fauxnance lookups, then falls back to last-known values and `market_quote_snapshots`, marking stale values. The contract exposes `partial`, `stale`, and `priceAsOf` semantics to make degraded pricing visible.

## Options considered

| Option | For | Against |
|---|---|---|
| Fail the whole response when any symbol cannot be priced | Strict correctness guarantee | Poor resilience; one missing symbol blocks all holdings visibility |
| Return only fresh prices and hide stale/missing values | Cleaner output | Hides degradation and confuses users when totals change unexpectedly |
| Return priced values with explicit stale/partial markers and nulls for unpriced symbols | High availability with transparent quality signals | More complex response logic and UI handling |

## Decision

Portfolio returns best-effort pricing with explicit stale/partial markers instead of all-or-nothing failure. The deciding factor was operational resilience while preserving user-visible truth about data quality.

## Consequences

- Users keep portfolio visibility during provider degradation.
- Cost: totals can exclude unpriced holdings and require careful UI messaging.
- Cost: stale interpretation becomes part of API behavior and test surface.
