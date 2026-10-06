# Sprint 7 Trade Executor Implementation Summary

## Files Created/Modified

### Configuration
- ✅ `pom.xml` - Updated with Spring Kafka, MyBatis, PostgreSQL, and testing dependencies
- ✅ `src/main/resources/application.properties` - Kafka, database, Fauxnance client configuration
- ✅ `src/test/resources/application-test.properties` - Test configuration

### Main Application
- ✅ `ExecutorApplication.java` - Spring Boot entry point with @MapperScan

### Domain Models (Pure Logic)
- ✅ `domain/Quote.java` - Quote representation (symbol, bid, ask)
- ✅ `domain/ExecutionDecision.java` - Pure decision function (NO Spring, DB, HTTP)
- ✅ `domain/ExecutionResult.java` - Execution outcome with status and price
- ✅ `domain/enums/ExecutionStatus.java` - FILLED, REJECTED

### Persistence Models
- ✅ `model/OrderRecord.java` - Database record for orders
- ✅ `model/PortfolioRecord.java` - Database record for portfolios
- ✅ `model/AccountRecord.java` - Database record for accounts
- ✅ `model/InstrumentRecord.java` - Database record for instruments

### MyBatis Mappers
- ✅ `mapper/OrderMapper.java` - Load orders by ID
- ✅ `mapper/PortfolioMapper.java` - Load portfolios by ID
- ✅ `mapper/AccountMapper.java` - Load accounts by ID
- ✅ `mapper/InstrumentMapper.java` - Load instruments by ID

### Services
- ✅ `service/TradeExecutionService.java` - Orchestration layer
  - Loads order + related entities
  - Validates execution-time conditions (account active, instrument tradable, affordability)
  - Obtains quote from Fauxnance
  - Applies pure decision function
  - Returns ExecutionResult

### Kafka Integration
- ✅ `event/OrderPlacedEvent.java` - Kafka event envelope with @JsonAnySetter
- ✅ `consumer/OrderPlacedConsumer.java` - Kafka listener
  - Topic: `orders`, Consumer group: `trade-executor`
  - Deserializes JSON, validates event type, extracts orderId
  - Tolerates unknown JSON fields
- ✅ `config/KafkaConsumerConfig.java` - Kafka configuration

### HTTP Client
- ✅ `client/FauxnanceQuoteClient.java` - Fauxnance quote service client
  - GET /quotes/{symbol}
  - Exponential backoff retry (100ms → 200ms → 400ms)
  - Max 3 retries (4 total attempts)
  - Scales prices to 2 decimals
  - Throws QuoteUnavailableException on failure
- ✅ `config/RestTemplateConfig.java` - Spring RestTemplate bean

### Exception Handling
- ✅ `exception/QuoteUnavailableException.java` - Thrown when quote unavailable

### Unit Tests (Pure logic, no Spring/DB)
- ✅ `ExecutionDecisionTest.java` - Tests all 6 decision paths
  1. BUY limit ≥ ask → FILLED at ask
  2. SELL limit ≤ bid → FILLED at bid
  3. Non-marketable BUY → REJECTED
  4. Non-marketable SELL → REJECTED
  5. Precision handling (decimal scaling)
  6. Invalid order side handling

- ✅ `QuoteTest.java` - Quote validation
  - Null/blank symbol detection
  - Null bid/ask detection
  - Precision handling

- ✅ `FauxnanceQuoteClientTest.java` - Quote client retry logic
  - Success on first attempt
  - Unavailable after max retries
  - Success after retry (retry recovery)
  - Null response handling

### Service Tests (With Mocks)
- ✅ `TradeExecutionServiceTest.java` - All 6 paths per story
  1. BUY affordable + marketable → FILLED
  2. SELL marketable → FILLED
  3. Non-marketable → REJECTED
  4. Insufficient funds → REJECTED
  5. Account suspended → REJECTED
  6. Quote unavailable → REJECTED
  + Additional scenarios:
    - Already filled/terminal order (no execution)
    - Instrument not tradable
    - Order not found
    - Portfolio not found
    - Account not found

### Integration Tests (With Mocked Fauxnance)
- ✅ `OrderPlacedConsumerIntegrationTest.java` - End-to-end flow tests
  - A: Quote available + marketable → FILLED
  - B: Quote available + non-marketable → REJECTED
  - C: Quote unavailable → retries → REJECTED
  - Unknown event type ignored
  - Null orderId ignored
  - Unknown JSON fields tolerated

### Documentation
- ✅ `README.md` - Comprehensive implementation guide

## Key Implementation Details

### 1. Execution-Time Validation (Per Sprint 5 Rules)

```java
// Account must be ACTIVE (not SUSPENDED/CLOSED)
if (!"ACTIVE".equalsIgnoreCase(account.getStatus())) {
    return REJECTED;
}

// Instrument must be tradable
if (!instrument.isTradable()) {
    return REJECTED;
}

// BUY: quantity × limitPrice must be affordable
BigDecimal cost = limitPrice.multiply(BigDecimal.valueOf(quantity));
if (!account.getBalance().canAfford(cost)) {
    return REJECTED;
}
```

### 2. Pure Decision Function (NO Spring, SQL, HTTP)

```java
public static ExecutionDecision decide(String orderSide, BigDecimal limitPrice, Quote quote) {
    if ("BUY".equalsIgnoreCase(orderSide)) {
        if (limitPrice.compareTo(quote.getAsk()) >= 0) {
            return FILLED at quote.getAsk();  // Use quoted price, not limit
        } else {
            return REJECTED;
        }
    }
    // Similar logic for SELL
}
```

### 3. Fauxnance Quote Retry Logic

```java
long backoffMs = initialBackoffMs;  // 100ms

for (int attempt = 0; attempt <= maxRetries; attempt++) {  // 4 total attempts
    try {
        Quote quote = restTemplate.getForObject(url, ...);
        return quote;
    } catch (RestClientException e) {
        if (attempt < maxRetries) {
            Thread.sleep(backoffMs);
            backoffMs = Math.min(backoffMs * 2, maxBackoffMs);  // Cap at 2000ms
        }
    }
}

throw new QuoteUnavailableException(...);  // After all retries exhausted
```

### 4. Decimal Precision Handling

Quote prices are scaled to 2 decimals (matching Account.balance):

```java
BigDecimal bid = response.bid.setScale(2, RoundingMode.HALF_UP);
BigDecimal ask = response.ask.setScale(2, RoundingMode.HALF_UP);
return new Quote(symbol, bid, ask);
```

### 5. Kafka Event Tolerance

```java
@JsonAnySetter
public void setAdditionalProperty(String name, Object value) {
    additionalProperties.put(name, value);  // Captures unknown fields
}
```

This allows the consumer to accept events with extra fields without failing.

### 6. Duplicate Protection (Replay-Safe)

```java
// Only process orders in NEW status
if (!"NEW".equalsIgnoreCase(orderRecord.getStatus())) {
    logger.info("Order {} has terminal status {}, skipping", orderId, status);
    return; // Kafka may redeliver; we silently skip
}
```

This prevents duplicate Kafka deliveries from causing multiple executions.

## Test Coverage

| Test Class | Test Count | Path Coverage |
|-----------|-----------|--|
| ExecutionDecisionTest | 10 | 6 paths + edge cases |
| QuoteTest | 7 | Validation + precision |
| FauxnanceQuoteClientTest | 5 | Retry logic |
| TradeExecutionServiceTest | 8 | 6 paths + edge cases |
| OrderPlacedConsumerIntegrationTest | 6 | End-to-end flows |
| **Total** | **36** | **Comprehensive** |

## Assembly-Line Style Separation of Concerns

The implementation follows a factory/assembly-line architecture:

1. **Kafka Consumer** - Receives raw events, deserializes, validates type
2. **Service Orchestration** - Loads entities, validates rules, orchestrates flow
3. **Quote Client** - Handles HTTP retry logic independently
4. **Mappers** - Persistence abstraction, pure database access
5. **Decision Function** - Pure business logic, testable in isolation
6. **Result** - Returns outcome for settlement (next sprint)

Each layer has clear responsibilities and can be tested independently.

## Assumptions & Gaps

### Assumptions Made
1. PostgreSQL database already contains tables: orders, portfolios, accounts, instruments
2. Kafka broker is running and reachable
3. Fauxnance service is available at the configured base URL
4. Account.balance uses 2-decimal precision (NUMERIC(19,2) or similar)
5. Order.status, Account.status are string columns with enum-like values

### Known Gaps (Expected for Next Sprint)
1. ❌ No database update of order status after execution
2. ❌ No cash debit for BUY orders
3. ❌ No position/holding updates for SELL orders
4. ❌ No publication of ORDER_FILLED/ORDER_REJECTED events
5. ❌ No transaction creation
6. ❌ No two-guard atomic settlement
7. ❌ No portfolio_holdings query for SELL validation (relies on Sprint 5 pre-validation)
8. ❌ No metrics/monitoring
9. ❌ No DLT (dead letter topic) for poison pills

## Design Decisions & Rationale

| Decision | Rationale |
|----------|-----------|
| Pure ExecutionDecision function | Enable TDD, instant testing, no infrastructure coupling |
| Decimal scaling in client | Match database precision automatically, centralized rounding |
| Retry inside client, not Kafka | Quote unavailability is business outcome, not Kafka error |
| No database writes | Settlement is atomic operation; executor only produces decision |
| Separate Record classes | Avoid coupling to Sprint 5 domain classes |
| @JsonAnySetter on events | Support contract evolution without breaking consumers |
| OrderRecord instead of Order | Simplify persistence layer, focus on what's needed |
| Exponential backoff | Reduce load on Fauxnance during transient failures |

## Running Tests

```bash
# Build and run unit tests
cd services/executor-service
mvn clean test

# Expected output: ~36 tests pass, all scenarios covered

# Build the package
mvn clean package

# Run the application
java -jar target/executor-0.0.1-SNAPSHOT.jar
```

## Next Steps (Settlement Story)

Once this executor is validated, the settlement story will:

1. Listen for ExecutionResult (internal event or database trigger)
2. Update order status to FILLED/REJECTED in database
3. Debit account cash and update balance for FILLED BUY orders
4. Update portfolio_holdings for FILLED SELL orders
5. Create transaction records
6. Publish ORDER_FILLED/ORDER_REJECTED Kafka events
7. Use distributed locking or optimistic locking for atomicity

The executor code does NOT need to change; settlement hooks into the result.

## Verification Checklist

- [x] Kafka consumer implemented (topic: orders, group: trade-executor)
- [x] ORDER_PLACED event deserialization with @JsonAnySetter
- [x] Order loading from PostgreSQL (NEW status check)
- [x] Instrument validation (exists, tradable)
- [x] Account validation (ACTIVE status)
- [x] Affordability validation (BUY: cost <= balance)
- [x] Quote client with Fauxnance API
- [x] Retry logic with exponential backoff and bounded budget
- [x] Quote price scaled to 2 decimals
- [x] Pure decision function (no Spring, DB, HTTP)
- [x] Unit tests for all 6 paths
- [x] Integration tests with mocked Fauxnance
- [x] Error handling for quote unavailability
- [x] Replay protection (NEW status check)
- [x] Comprehensive documentation

