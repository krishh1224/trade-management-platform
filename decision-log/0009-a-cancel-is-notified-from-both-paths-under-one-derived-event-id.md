# 0009 A cancel is recorded by the trade API at once and again by the executor, under one derived event id

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | krishh1224 (notifications and executor change) |

## Context

`DELETE /api/v1/orders/{id}` commits `NEW → CANCELLED`, and the trade API then publishes
`ORDER_CANCELLED` to `orders`. Notifications reads only `trade-events`, and the only writer of
`trade-events` is the executor (kafka.md). So the executor was changed to read the cancel, confirm in the
database that the order exists, belongs to the account and is `CANCELLED`, and then publish to
`trade-events`. On this path the customer's cancel notification waits on the executor being up and caught
up on `orders`. That makes a cancel the slowest notification, even though it is the one the customer has
just asked for.

## Options considered

| Option | For | Against |
|---|---|---|
| Executor path only | One path; one writer per topic respected | The notification is as late as the executor, and missing entirely while it is down |
| Trade API publishes the cancel to `trade-events` itself | Fast; one path | Two writers on `trade-events`, breaking the rule the ACLs plan is built on |
| Trade API records the notification directly, and the executor path is dropped | Fast, no Kafka hop | `trade-events` no longer carries cancels for other consumers (portfolio, analytics) |
| Both: the trade API records it in-process right after the commit, the executor still announces it, and both use the same id, `UUID.nameUUIDFromBytes("ORDER_CANCELLED:" + orderId)` | Immediate in the normal case. The executor path covers a failed immediate record. 0007 makes the second arrival a no-op | The same id function lives in two services and must never drift |

## Decision

Both paths record the cancel, and the derived id makes whichever arrives second insert nothing. The
executor-only option was nearest. It lost because of the latency on the notification the customer
actively waits for. The one-writer rule ruled out the trade API publishing to `trade-events` itself.

## Consequences

- One notification per cancelled order, whichever path wins. A failure on the immediate path is only
  logged: the cancel still succeeds, and the executor delivers.
- Cost: `OrderIdentifiers.cancelledEventId` (order-service) and `TradeEventPublisher.cancelledEventId`
  (executor-service) are duplicate code across two services. Each is pinned by a test to the same
  literal value. Changing one without the other silently doubles every cancel notification.
- The message text comes from whichever path arrives first. Both build it from the same structured
  fields through `MessageFormatter`.
