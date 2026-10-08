# 0603 Strategies evaluate under row lock and skip trading on stale backlog quotes

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-08 |

## Context

Two safety issues exist in strategy execution: disable races (order placed after disable response) and stale backlog execution after downtime. Strategy code and tests implement row locking for per-strategy decision paths and a `max-quote-age` threshold to avoid trading on old consumed quotes while still storing history.

## Options considered

| Option | For | Against |
|---|---|---|
| No row lock, evaluate from cycle snapshot | Simpler flow | Race with disable operations can place unwanted orders |
| Drop backlog quotes entirely | Prevents stale trades | Loses indicator continuity and chart/replay history |
| Lock strategy row during decision and store backlog history but skip stale trade actions | Deterministic disable behavior and safer execution timing | More concurrency logic and age-policy tuning complexity |

## Decision

Decisions run under strategy-row lock, and stale backlog quotes are stored but not traded when older than the configured age threshold. The deciding factor was customer safety without losing diagnostic/history continuity.

## Consequences

- Disable semantics become deterministic at API boundary.
- Cost: disable can wait behind one in-flight decision.
- Cost: some historically valid signals are intentionally not acted on after downtime.
