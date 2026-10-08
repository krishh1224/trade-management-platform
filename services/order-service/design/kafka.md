# Kafka Design Decisions — Sprint 7

## Topic creation command

From the repository root, with the Kafka container running and healthy:

```bash
docker-compose -f docker-compose.kafka.yml up -d
chmod +x infrastructure/kafka/scripts/create-topics.sh
./infrastructure/kafka/scripts/create-topics.sh
```

This creates all six topics (three main, three dead-letter) against an empty
broker. It is safe to re-run — `--if-not-exists` prevents errors if a topic
already exists. After a broker volume reset (`docker volume rm kafka-data`),
run this script again before starting any producer or consumer.

## Key choices

### `orders` and `trade-events` — keyed by `accountId`

The key decides the partition, and the partition decides the ordering.
Kafka guarantees message ordering within a partition and makes no ordering
promise across partitions.

The ordering that matters for orders and trade events is **per account**.
Two orders on the same account must be executed in the sequence they were
accepted. If they land on different partitions, two executor threads can
process them concurrently, and a sell could be processed before the buy
that made it possible. Keying by `accountId` ensures all messages for one
account land on the same partition, so Kafka's within-partition ordering
gives us per-account ordering.

**Why not key by `orderId`?** Every order has a unique ID, so every message
would hash to a different partition. Two orders on the same account could
land on different partitions and be processed out of order. The per-account
ordering guarantee would be gone entirely.

### `market-data` — keyed by `symbol`

The ordering that matters for price data is **per instrument**. A consumer
must never see an older quote for `AAPL` after a newer one. Keying by
`symbol` ensures all quotes for one instrument land on the same partition,
preserving the time-ordered sequence per symbol. Quotes for different
symbols are independent and can be processed in parallel.

## Partition counts

### `orders` — 3 partitions

Three partitions allows up to three executor instances to consume in
parallel within the `trade-executor` consumer group. Each instance gets
assigned one partition. Adding a fourth executor instance would leave it
idle, because a consumer group cannot have more active members than
partitions — the extra member gets no partition assignment.

Three is enough to demonstrate consumer-group rebalancing (start two
instances, add a third, watch partitions redistribute) without being so
many that a local development machine runs out of resources.

### `trade-events` — 3 partitions

Matches `orders` for consistency. The same account-keying logic applies.
Multiple downstream consumer groups (portfolio service, notification
service, analytics loader) each read all three partitions independently.

### `market-data` — 6 partitions

Higher partition count reflects higher message volume. The poller
publishes one message per symbol per poll interval, potentially every
60 seconds. Six partitions lets up to six consumer instances share the
load if Sprint 10 extensions need it.

### What happens if we increase partition counts in Sprint 10

Partitions can be increased but **never decreased**. Increasing the count
changes the hash function that maps keys to partitions. An account that
was assigned to partition 0 before the change may map to partition 2
after it. This means:

- **Per-account ordering splits at the resize boundary.** Messages before
  the change are on the old partition; messages after may be on a different
  one. A consumer reading from the beginning would see the account's
  history split across two partitions.
- **Consumers that depend on per-account ordering would notice.** The
  executor depends on processing orders for one account in sequence. After
  a partition increase, new orders for an account may land on a different
  partition than older ones, so the ordering guarantee only holds within
  each partition's segment, not across the boundary.
- **`market-data` consumers would notice too.** A symbol whose quotes were
  on partition 2 may move to partition 5, so the per-symbol ordering
  guarantee splits at the boundary.

This is why the partition count is chosen deliberately now and not
changed casually later.

## Dead-letter topics

| Topic | Partitions | Retention |
|---|---|---|
| `orders.DLT` | 1 | 14 days |
| `trade-events.DLT` | 1 | 14 days |
| `market-data.DLT` | 1 | 14 days |

**1 partition per DLT** because dead-letter topics are low volume — they
only receive messages that failed processing. A single partition is enough
to handle the expected throughput and keeps things simple. Ordering within
the DLT is not critical since these messages are investigated manually,
not processed by an automated consumer pipeline. All failed messages land
on one partition regardless of their original key, which makes it easy to
read the entire DLT in one pass when investigating failures.

**Retention is 14 days** — longer than any source topic. A dead-lettered
message needs to survive long enough for the team to investigate the
failure. If the DLT retention were shorter than the source topic's
retention, a failed message could expire from the DLT before anyone
looked at it. 14 days gives two full working weeks for investigation,
regardless of when in the sprint the failure occurred.

## Auto-creation

Topic auto-creation is **disabled** on the broker
(`KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`). An auto-created topic arrives
with 1 partition and default retention, silently, on first use. With it
off, a producer writing to a topic that nobody created gets an error —
which is a failure we can detect and fix, unlike a wrong-shaped topic
that silently works until someone checks the partition count.

## Producer configuration

All producers in the platform use:

| Setting | Value | Why |
|---|---|---|
| `acks` | `all` | Message is acknowledged only after all in-sync replicas confirm. Prevents data loss. |
| `enable.idempotence` | `true` | Deduplicates messages caused by producer retries (network timeouts). Does not deduplicate application-level retries. |
| `retries` | `2147483647` (max int) | Retry indefinitely on transient broker errors; let `delivery.timeout.ms` be the real bound. |
| `max.in.flight.requests.per.connection` | `5` | Maximum allowed with idempotent producer. Higher values risk reordering on retry without idempotence. |

## Consumer configuration

| Setting | Value | Why |
|---|---|---|
| `enable.auto.commit` | `false` | Offsets are committed after processing, not before. Committing first risks losing a message on crash; committing after risks reprocessing, which is handled by idempotent consumers. |
| `auto.offset.reset` | `earliest` | On first join or after offset expiry, start from the beginning rather than missing messages. |
| `group.id` | Explicit per consumer | Each logical consumer has its own group ID so offsets are independent. |

## Security (production plan, not implemented locally)

Local development runs plaintext with no authentication. In production:

- TLS between all clients and brokers
- SASL for client authentication
- Per-topic ACLs: only Trade REST API writes to `orders`, only Trade
  Executor reads from `orders`, only Trade Executor writes to
  `trade-events` and `market-data`

No credentials, full names, email addresses, or API keys appear in any
message payload.