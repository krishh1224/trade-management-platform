# 0004 Watchlists hands a triggered alert to notifications inside the quote's transaction, not over Kafka

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | krishh1224 (author); Riya Rangwani (reviewed and merged PR #26) |

## Context

`MarketDataQuoteListener` consumes `market-data` (6 partitions, keyed by symbol). For each quote it
stores the new last price in `watchlist_quotes` and flips every crossed alert from `ACTIVE` to
`TRIGGERED`. The customer must then be told through notifications. If an alert is marked `TRIGGERED`
and the hand-off is lost, the alert is retired without anyone ever hearing about it. The opposite
failure, a message without the state change, sends the same alert again on the next quote.
`trade-events` has a single writer, the executor (kafka.md), and topics are created by hand.

## Options considered

| Option | For | Against |
|---|---|---|
| Publish `PRICE_ALERT_TRIGGERED` to a new `alerts` topic that notifications consumes | Decoupled; a notifications outage does not touch the `market-data` consumer | A new topic and DLT in `create-topics.sh`. The database commit and the Kafka send are not atomic: without an outbox table, a crash between them leaves `TRIGGERED` with no message |
| Publish onto `trade-events` | Notifications already consumes it | Breaks the one-writer-per-topic rule; `trade-events` is keyed by account, `market-data` by symbol |
| Call `AlertDelivery.deliver(PriceAlert)` in-process, inside the same database transaction as the price update and the state flip | The alert state, the stored price and the `QUEUED` notification row commit or roll back together. No new topic | Notifications' insert sits on the `market-data` critical path. Works only while both modules share a process and a database (0001) |

## Decision

Watchlists calls `AlertDelivery.deliver` inside the quote's transaction. If it throws, the whole quote
rolls back and is not acknowledged. The container retries it 3 times, 1 s apart, then parks it on
`market-data.DLT`. A topic plus an outbox was the nearest option. What decided it: a single database
transaction gives the "both or neither" guarantee for free, while the topic route needs an outbox to
get the same guarantee, and that outbox is more code than the whole hand-off.

## Consequences

- A per-alert `delivery_id` is passed as the notification's `event_id`, so a redelivered quote that
  reaches `deliver` again inserts nothing (0007).
- Because the stored price rolls back with the alert, the next quote is compared against the old price.
  If the price is still beyond the threshold, the crossing is found again: the alert is late, not lost.
- Cost: if notifications cannot insert for longer than the retries and the price moves back before the
  next quote, the crossing is never seen again. The only trace is the quote on `market-data.DLT`.
  Nobody watches the DLT automatically.
- Cost: an outage in the notifications table now stalls price alerts for the affected partition
  during the retries.
- Constraint: watchlists never formats text or looks up a channel or contact details. `PriceAlert`
  carries structured fields only, and notifications writes the message (0003, 0008).
