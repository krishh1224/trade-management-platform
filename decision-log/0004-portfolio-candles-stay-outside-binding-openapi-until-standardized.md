# 0404 Portfolio candles stay outside the binding OpenAPI contract until standardized

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |

## Context

The frontend needs `GET /api/v1/portfolio/{accountId}/candles/{symbol}` for charting, but the binding contract (`contracts/portfolio-api.yaml`) does not include this route. The module README documents this endpoint explicitly as a temporary contract gap. Regenerating clients and widening contract review scope mid-sprint carries delivery risk.

## Options considered

| Option | For | Against |
|---|---|---|
| Add candles to binding OpenAPI immediately | Single canonical contract | Late contract churn and client regeneration risk |
| Keep route undocumented in contracts and remove it from implementation | Strict contract parity | Blocks chart functionality already integrated in UI |
| Keep route implemented and documented in module README until contract owner schedules standardization | Delivers feature now with explicit temporary gap | Two documentation locations must be tracked |

## Decision

Keep candles route implemented and documented at module level now, and defer binding OpenAPI adoption to a planned contract update. The deciding factor was minimizing sprint-time contract blast radius while shipping required UX.

## Consequences

- Chart feature can ship without immediate contract regeneration.
- Cost: contract-first consumers do not discover this route from central OpenAPI.
- Follow-up: promote route into `contracts/portfolio-api.yaml` in a controlled contract update window.
