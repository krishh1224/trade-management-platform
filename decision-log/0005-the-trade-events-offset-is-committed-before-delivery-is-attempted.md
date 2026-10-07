# 0005 The trade-events offset is committed once a notification is recorded, before any delivery is attempted

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | krishh1224 (notifications) |

## Context

Notifications consumes `trade-events` (3 partitions, keyed by `accountId`) in its own consumer group,
`notifications`, with auto-commit off (kafka.md). Delivery goes to external providers. SMTP has 5 s
connect, read and write timeouts. Web Push has a 10 s timeout per browser and can fan out to several
browsers per account. With 3 partitions, one blocked partition stops roughly a third of all accounts.
The container's error handler retries a failed record 3 times, 1 s apart, then sends it to
`trade-events.DLT`.

## Options considered

| Option | For | Against |
|---|---|---|
| Send from the listener, then acknowledge | One step; the offset means "delivered" | A slow or dead provider holds the partition, so every account on it waits behind one email. A provider error becomes a Kafka retry and then a DLT entry, mixing "could not read this event" with "the email server was down" |
| Record a `QUEUED` row, commit, acknowledge; a separate scheduled dispatcher sends | The partition only waits on our own database. Provider outages show up as row status, per notification | Two moving parts; up to 5 s added latency; the dispatcher assumes a single instance |

## Decision

The listener records the row and acknowledges. `NotificationDispatcher` sends every 5 s. The offset says
what has been read, and the `status` column says what reached the customer. What decided it: under
send-then-ack, an outage at one provider stops a third of the platform's notifications. Under
record-then-ack, the same outage marks rows `FAILED` and nothing else is affected.

## Consequences

- The DLT now only holds events that could not be recorded: malformed JSON, a missing `eventId` or
  account, or the database being down past the retries. Provider failures never reach it.
- What happens to a failed send is decided separately, in 0006.
- Cost: the dispatcher must be the only instance. With two Trade REST API replicas, both would send
  the same pending rows. The fix is to claim rows with `SELECT ... FOR UPDATE SKIP LOCKED`, and it is
  not built.
- Cost: outbound delivery is at-least-once. If the process dies after the provider accepts a message
  but before `SENT` is written, that message is sent again on restart.
