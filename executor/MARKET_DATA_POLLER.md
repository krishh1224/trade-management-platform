# Market Data Poller Architecture - Sprint 7+

## Overview

The Trade Executor now includes an independent scheduled market-data poller that runs on a fixed interval inside the existing Spring Boot runtime. This poller fetches live quotes for symbols that someone holds, and publishes one Kafka message per quote to the `market-data` topic.

**Key Design Principle**: The poller runs independently of order placement and does NOT couple polling to ORDER_PLACED events.

## Architecture

```
MarketDataPoller (scheduled)
    |
    | every POLL_INTERVAL_SECONDS
    v
SymbolProvider
    |
    v
database query: portfolio_holdings + instruments
    |
    v
Set of distinct held symbols (deduplicated)
    |
    v
Batch symbols (max 25 per HTTP request)
    |
    v
For each batch:
    |
    +---> For each symbol:
    |     |
    |     v
    |     FauxnanceQuoteClient.getQuote(symbol)
    |     |
    |     v
    |     MarketDataPublisher.publishQuote(symbol, quote)
    |         |
    |         +---> Kafka market-data topic
    |         |     Key: symbol
    |         |     Value: KafkaEnvelope<QuotePayload>
    |         |
    |         v
    |     (One Kafka message per quote)
    |
    v
Poll complete
```

## Components

### 1. SymbolProvider (Service)

**Purpose**: Determines which symbols to poll for market data.

**Location**: `service/SymbolProvider.java`

**Responsibility**:
- Query portfolio holdings from database
- Return deduplicated set of symbols that someone holds

**API**:
```java
Set<String> getSymbolsToPoll()
```

**Implementation**:
- Calls `PortfolioHoldingMapper.findAllHeldSymbols()`
- SQL query: `SELECT DISTINCT i.ticker FROM portfolio_holdings ph JOIN instruments i ...`
- Filters by `quantity > 0` to exclude zero/closed positions

### 2. MarketDataPoller (Component)

**Purpose**: Scheduled task that orchestrates polling.

**Location**: `service/MarketDataPoller.java`

**Annotations**:
- `@Component` - Registered as Spring bean
- `@Scheduled(fixedRateString = "${market-data.poll-interval-seconds:60}", timeUnit = SECONDS)` - Runs on schedule

**Responsibilities**:
1. Get symbols from SymbolProvider
2. Batch symbols (max 25 per batch)
3. For each symbol, call FauxnanceQuoteClient
4. Publish each quote to Kafka via MarketDataPublisher
5. Handle failures gracefully (per-symbol, don't cascade)

**Key Methods**:
- `pollMarketData()` - Main scheduled method
- `batchSymbols()` - Partition symbols into batches of max 25
- `validatePollInterval()` - Called at startup to enforce quota

**Error Handling**:
- Per-symbol failures don't stop polling of others
- Failed quote fetch caught and logged, continues to next symbol
- Kafka publish failures don't stop the poller

### 3. MarketDataPublisher (Component)

**Purpose**: Publishes individual QUOTE events to Kafka.

**Location**: `producer/MarketDataPublisher.java`

**Responsibilities**:
- Serialize quote into KafkaEnvelope<QuotePayload>
- Publish to `market-data` topic with symbol as key
- One Kafka message per quote (NOT batched)

**API**:
```java
void publishQuote(String symbol, Quote quote)
```

**Kafka Contract**:
- Topic: `market-data`
- Key: symbol (e.g., "INFY.NS")
- Value: JSON-serialized `KafkaEnvelope<QuotePayload>`
- Event Type: `QUOTE`
- One message per quote (never batched)

### 4. PortfolioHoldingMapper (Enhanced)

**Purpose**: Database access for holdings.

**New Method**:
```java
@Select("SELECT DISTINCT i.ticker ...")
List<String> findAllHeldSymbols()
```

**Query**:
```sql
SELECT DISTINCT i.ticker
FROM portfolio_holdings ph
JOIN instruments i ON ph.instrument_id = i.instrument_id
WHERE ph.quantity > 0
ORDER BY i.ticker
```

## Configuration

### Properties

All three application profiles support:

```properties
market-data.poll-interval-seconds=${MARKET_DATA_POLL_INTERVAL_SECONDS:60}
```

- Default: 60 seconds
- Minimum enforced: 44 seconds (validates at startup)
- Environment override: `MARKET_DATA_POLL_INTERVAL_SECONDS`

### Validation

At startup, `MarketDataPoller` constructor validates:
1. `pollIntervalSeconds >= MIN_POLL_INTERVAL_SECONDS` (44 seconds)
2. Throws `IllegalArgumentException` if invalid
3. Logs validation result at INFO level

**Rationale**: Prevents configuration errors that would exceed Fauxnance quota.

## Kafka Contract

### Topic: market-data

**Message Structure**:
```
Key:   "INFY.NS"  (the symbol)
Value: JSON envelope
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "QUOTE",
  "eventTime": "2024-01-15T10:30:45.123Z",
  "source": "executor",
  "schemaVersion": 1,
  "payload": {
    "symbol": "INFY.NS",
    "bid": 1500.50,
    "ask": 1501.00
  }
}
```

**One Message Per Quote**:
- Each symbol gets exactly one Kafka message per polling cycle
- Batching happens at HTTP level (Fauxnance), not at Kafka level
- Bad: one message per HTTP batch with array of quotes
- Good: one message per symbol with individual quote

## Quota Management

### Daily Quota

Fauxnance allows: **2000 requests per day**

### Quota Arithmetic

For `N` distinct symbols and interval `I` seconds:

```
Batches per poll = ceil(N / 25)
Polls per day = floor(86400 / I)
Requests per day = Batches per poll × Polls per day

Constraint: Requests per day ≤ 2000
```

### Declared Symbol Set

From seed data, there are **6 symbols**:
1. INFY.NS
2. RELIANCE.NS
3. TATASTEEL.BO
4. HDFCBANK.NS
5. ICICIBANK.NS
6. ITC.NS

### Quota Calculation: 6 Symbols

| Interval | Batches/Poll | Polls/Day | Requests/Day | Status |
|----------|--------------|-----------|--------------|--------|
| 30 sec   | 1            | 2880      | 2880         | ❌ EXCEEDS |
| 43 sec   | 1            | 2009      | 2009         | ❌ EXCEEDS |
| 44 sec   | 1            | 1963      | 1963         | ✅ OK |
| 60 sec   | 1            | 1440      | 1440         | ✅ OK |
| 120 sec  | 1            | 720       | 720          | ✅ OK |

**Minimum interval: 44 seconds** (enforced in code)

**Default interval: 60 seconds** (comfortable margin within quota)

### Why 44 Seconds?

- `86400 / 2000 = 43.2` (theoretical minimum)
- Round up to 44 for safety margin
- At 44 seconds: `floor(86400/44) = 1963` polls/day
- `1 batch × 1963 polls = 1963 requests/day ≤ 2000` ✅

### Future Symbol Growth

If symbols grow, the minimum interval must increase:

**Example: 8 symbols**
- Batches per poll: `ceil(8/25) = 1` (still fits in one batch)
- At 44 sec: `1 × 1963 = 1963` requests/day ✅
- At 43 sec: `1 × 2009 = 2009` requests/day ❌

**Example: 26 symbols**
- Batches per poll: `ceil(26/25) = 2` (need two HTTP requests)
- For 2000 quota: `2 × floor(86400/I) ≤ 2000`
- `floor(86400/I) ≤ 1000`
- `I ≥ 86400/1000 = 86.4` → **87 seconds minimum**

## Symbol Selection

### Held Symbols

Symbols are sourced from **portfolio_holdings** joined with **instruments**:
- Portfolio must exist (one per account)
- Portfolio holding must have `quantity > 0`
- Instrument must exist and be tradable (checked by DB foreign keys)

### Deduplication

- SQL query uses `DISTINCT` to eliminate duplicates
- `SymbolProvider` converts to `Set<String>` for additional safety
- Result: one API call per unique symbol per poll cycle

### No Watchlist

**Current Implementation**: Only holds are polled.
**Future**: If watchlist table is added, `SymbolProvider.getSymbolsToPoll()` can union holdings + watchlist.

The code is designed to accommodate this without major refactoring.

## Batch Size

### Maximum: 25 Symbols per HTTP Request

- Fauxnance batch endpoint (not single-symbol) accepts up to 25
- `MarketDataPoller.batchSymbols()` partitions into lists of max 25
- Each batch triggers a separate `FauxnanceQuoteClient.getQuote()` call

### Examples

| Symbols | Batches | HTTP Calls |
|---------|---------|-----------|
| 1-25    | 1       | 1         |
| 26      | 2       | 2 (25+1)  |
| 50      | 2       | 2 (25+25) |
| 51      | 3       | 3 (25+25+1) |

## Error Handling

### FauxnanceQuoteClient Failures

When a quote fetch fails:
1. Logged at WARN level
2. Caught as `QuoteUnavailableException`
3. Failure **does not stop** polling of other symbols
4. Failure count incremented
5. Polling continues to next symbol

### MarketDataPublisher Failures

When Kafka publish fails:
1. Logged at ERROR level
2. Caught and suppressed (no re-throw)
3. Failure **does not stop** polling
4. Failure count incremented
5. Polling continues to next symbol

### Overall Polling Failure

If an unexpected exception escapes:
1. Caught at `pollMarketData()` level
2. Logged at ERROR level
3. Scheduler continues (next poll will retry)

### Summary Logging

After each poll cycle:
```
Market-data polling completed: X successful, Y failed
```

## Reuse of Existing Components

### FauxnanceQuoteClient

- ✅ Reused as-is
- Provides exponential backoff retry logic
- Handles HTTP errors and timeouts
- Scales prices to 2 decimal places

### KafkaTemplate & Configuration

- ✅ Reused as-is
- Configured for JSON serialization
- Producer ACKs set to "all"
- Handles serialization and send semantics

### KafkaEnvelope & QuotePayload

- ✅ Envelope reused (same structure as ORDER_FILLED events)
- Payload simplified to `{symbol, bid, ask}`
- Matches existing market-data contract from trade-api

## Spring Configuration

### Scheduling

- `@EnableScheduling` added to `ExecutorApplication`
- Enables annotation-based scheduled task execution
- Uses default `TaskScheduler` (single thread by default)

### Application Properties

```properties
market-data.poll-interval-seconds=60
```

- Injected via `@Value` into `MarketDataPoller` constructor
- Can be overridden per environment

## Testing

### Unit Tests

#### MarketDataPollerTest (15 tests)

1. Empty symbol set → no calls
2. Single symbol → fetched and published
3. Batch of 25 → all fetched
4. Batch of 26 → split correctly (25+1)
5. Batch of 50 → split correctly (25+25)
6. Duplicate symbols → fetched only once
7. Each quote → published as own message
8. Each message → keyed by symbol
9. Failed fetch → continues polling
10. Minimum interval enforced (< 44s rejected)
11. Minimum interval accepted (44s)
12. Default interval (60s) within quota
13. Quota arithmetic for 6 symbols
14. Minimum interval for 8 symbols
15. Poll execution completes without throwing

#### SymbolProviderTest (6 tests)

1. Empty holdings → empty set
2. Single holding → single symbol
3. Multiple holdings → all symbols
4. Deduplication works
5. Six symbols from seed data
6. Set operations correct

### Integration Tests (Future)

- Test with real database (order-independent)
- Test with real Kafka (publish verification)
- Test scheduler invocation (timing verification)

## Files Modified & Created

### New Files

| File | Purpose |
|------|---------|
| `service/SymbolProvider.java` | Query held symbols |
| `service/MarketDataPoller.java` | Scheduled polling orchestration |
| `producer/MarketDataPublisher.java` | Kafka publishing for market-data |
| `test/service/MarketDataPollerTest.java` | 15 unit tests |
| `test/service/SymbolProviderTest.java` | 6 unit tests |

### Modified Files

| File | Change |
|------|--------|
| `ExecutorApplication.java` | Added `@EnableScheduling` |
| `mapper/PortfolioHoldingMapper.java` | Added `findAllHeldSymbols()` method |
| `application.properties` | Added `market-data.poll-interval-seconds` |
| `application-local.properties` | Added `market-data.poll-interval-seconds` |
| `application-platform.properties` | Added `market-data.poll-interval-seconds` |

## Deployment Considerations

### No Docker Changes

- ✅ No new containers added
- ✅ No new services in docker-compose
- ✅ No new network requirements
- Runs inside existing Trade Executor container

### Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `MARKET_DATA_POLL_INTERVAL_SECONDS` | 60 | Polling interval |

### Database

- ✅ No schema changes
- Uses existing `portfolio_holdings` and `instruments` tables
- No new columns or tables required

## Future Enhancements

### Watchlist Support

If a watchlist table is added:

```sql
CREATE TABLE watchlist (
  watchlist_id BIGINT PRIMARY KEY,
  account_id BIGINT NOT NULL,
  instrument_id BIGINT NOT NULL,
  created_at TIMESTAMP,
  ...
);
```

Extend `SymbolProvider`:

```java
Set<String> getSymbolsToPoll() {
    var held = holdingMapper.findAllHeldSymbols();
    var watched = watchlistMapper.findAllWatchedSymbols();
    return union(held, watched);
}
```

### Rate Limiting & Throttling

If quota becomes tight, could add per-batch delay or adaptive batching.

### Caching

Could cache symbol list to reduce DB queries, though database cost is minimal.

### Parallel Batching

Currently sequential. Could make batches parallel (careful with quota).

---

## Quota Reference

**Data Point**: 6 symbols, 60-second interval
- Polls per day: 1440
- Requests per day: 1440
- Utilization: 72% of 2000-request quota
- Comfortable margin for testing and growth
