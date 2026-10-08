# 0007 A unique event id on the notifications table makes every redelivery a no-op

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | krishh1224 (notifications) |

## Context

Consumers commit offsets after processing (kafka.md), so a crash, a rebalance or a deliberate rewind
redelivers events. The acceptance criteria require that a replayed event sends no second message.
Notifications are created along three paths: `trade-events`, the trade API's immediate cancel record
(0009), and watchlists' `AlertDelivery.deliver` (0004). Two redeliveries of the same event can be
processed concurrently, for example during a rebalance. Every platform envelope carries an `eventId`.

## Options considered

| Option | For | Against |
|---|---|---|
| Check first: `SELECT` by event id, insert if absent | Easy to read | Two concurrent redeliveries can both see "absent" and both insert |
| Kafka transactions / exactly-once consumption | Built into the platform | Covers only the Kafka path, not the two in-process paths. The executor's producers are not transactional |
| `UNIQUE (event_id)` and `INSERT ... ON CONFLICT (event_id) DO NOTHING`; the caller supplies a stable id | The database settles races. The same rule covers all three paths. The insert reports whether anything was new | Every producer must send an id that stays stable across retries |

## Decision

`notifications.event_id` is `UNIQUE`, and the only insert is `ON CONFLICT DO NOTHING`. A trade event uses
its envelope `eventId`, a price alert uses its per-alert `delivery_id`, and a cancel uses an id derived
from the order (0009). The check-first option was nearest. It lost because only a constraint stops two
concurrent redeliveries from both inserting.

## Consequences

- Replay is safe to demonstrate: reset the `notifications` group to earliest, and the log shows
  "Duplicate delivery ... ignored" with an unchanged row count.
- Constraint: `event_id` is a `UUID`. A producer that sends a non-UUID id is rejected to the DLT.
- Cost: correctness depends on producers. An application-level retry that mints a fresh `eventId`
  for the same fill would create a second notification, and nothing here can detect that.
  The idempotent Kafka producer only deduplicates its own network retries.
