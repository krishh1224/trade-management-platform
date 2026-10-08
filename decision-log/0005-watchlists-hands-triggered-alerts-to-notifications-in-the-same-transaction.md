# 0501 Watchlists hands triggered alerts to notifications inside the same quote transaction

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |

## Context

Price alerts are triggered while processing `market-data` quotes keyed by symbol. If alert state flips to `TRIGGERED` but notification handoff is lost, the customer misses the alert. If notification is emitted without state update, duplicate customer messages can occur on subsequent quotes. The current architecture already supports in-process module calls and shared transaction boundaries.

## Options considered

| Option | For | Against |
|---|---|---|
| Publish to a separate Kafka topic and let notifications consume | Decoupled and outage-tolerant | Requires outbox/atomicity work to avoid lost handoff |
| Publish onto existing `trade-events` | Reuses existing consumer path | Breaks one-writer-per-topic rule and keying assumptions |
| In-process delivery call inside quote transaction | Atomic state flip + handoff persistence in one commit | Couples alert processing path to notification insert availability |

## Decision

Use in-process handoff (`AlertDelivery`) inside the quote transaction so alert state transition and notification record succeed or fail together. The deciding factor was atomicity with least additional infrastructure.

## Consequences

- No silent "triggered but never queued" alert failures inside successful quote commits.
- Cost: notification persistence problems can delay alert progression on affected partition retries.
- Cost: approach depends on current single-process modular deployment.
