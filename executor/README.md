# Trade Executor Service - Sprint 7 Implementation

## Overview

The Trade Executor is a Spring Boot microservice that consumes `ORDER_PLACED` events from Kafka, loads orders from PostgreSQL, validates execution-time conditions, obtains live market quotes from Fauxnance, applies a pure decision function, and resolves orders as FILLED or REJECTED.

## Architecture

### Key Components

1. **OrderPlacedConsumer** - Kafka consumer that:
   - Listens to `orders` topic with consumer group `trade-executor`
   - Deserializes JSON envelopes with tolerance for unknown fields
   - Validates event type and extracts orderId
   - Delegates to TradeExecutionService

2. **TradeExecutionService** - Orchestration layer that:
   - Loads order, portfolio, account, and instrument from PostgreSQL
   - Validates execution-time conditions (account active, instrument tradable, affordability)
   - Obtains quote from Fauxnance with retries
   - Applies pure decision function
   - Returns ExecutionResult

3. **FauxnanceQuoteClient** - HTTP client that:
   - Calls Fauxnance `/quotes/{symbol}` endpoint
   - Implements exponential backoff retry logic (max 3 retries)
   - Scales prices to 2 decimal places for consistency
   - Throws QuoteUnavailableException on failure

4. **ExecutionDecision** - Pure business logic (NO database, HTTP, or Spring):
   - Decides FILLED/REJECTED based on order side and market quote
   - BUY: limitPrice >= ask → FILLED at ask
   - SELL: limitPrice <= bid → FILLED at bid
   - Always uses quoted execution price, not limit price

5. **MyBatis Mappers** - Persistence layer:
   - OrderMapper: Loads orders by ID
   - PortfolioMapper: Loads portfolios by ID
   - AccountMapper: Loads accounts by ID
   - InstrumentMapper: Loads instruments by ID

## Database Schema

The executor connects to an existing PostgreSQL database. Expected tables:

```sql
-- orders table
SELECT order_id, portfolio_id, instrument_id, quantity, price, status, side, 
       idempotency_key, created_at, updated_at, expires_at FROM orders;

-- portfolios table
SELECT portfolio_id, name, account_id, status, created_at, updated_at FROM portfolios;

-- accounts table
SELECT acc_id, client_id, account_number, risk_profile, base_currency, 
       balance, status, created_at, updated_at FROM accounts;

-- instruments table
SELECT instrument_id, ticker, name, asset_class, currency, exchange, tradable FROM instruments;
```

## Kafka Configuration

- **Topic**: `orders`
- **Consumer Group**: `trade-executor`
- **Message Key**: `accountId`
- **Message Format**: JSON envelope with `eventType`, `orderId`, `accountId`, `timestamp`
- **Tolerance**: Unknown JSON fields are captured via `@JsonAnySetter`

### Event Example

```json
{
  "eventType": "ORDER_PLACED",
  "orderId": 12345,
  "accountId": 67890,
  "timestamp": 1694768400000,
  "unknownField": "ignored"
}
```

## Execution-Time Validation Rules

1. **Order Status**: Must be NEW (prevents duplicate execution)
2. **Account**: Must be ACTIVE
3. **Instrument**: Must exist and be tradable
4. **Affordability (BUY)**: `quantity × limitPrice <= accountBalance`
5. **Quote**: Must be available within retry budget

## Decision Function Rules

### BUY Orders
- If `limitPrice >= ask` → **FILLED at ask price**
- Otherwise → **REJECTED** (non-marketable)

### SELL Orders
- If `limitPrice <= bid` → **FILLED at bid price**
- Otherwise → **REJECTED** (non-marketable)

## Configuration

### application.properties

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/trading_platform
spring.datasource.username=postgres
spring.datasource.password=postgres

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=trade-executor

# Fauxnance Client
fauxnance.base-url=http://localhost:8081
fauxnance.quote.max-retries=3
fauxnance.quote.initial-backoff-ms=100
fauxnance.quote.max-backoff-ms=2000
```

## Building

```bash
cd executor
mvn clean compile
```

## Testing

### Run All Tests

```bash
mvn test
```

### Test Coverage

#### Unit Tests (No Spring, No Database)

1. **ExecutionDecisionTest** (6 paths):
   - BUY limit ≥ ask → FILLED at ask
   - SELL limit ≤ bid → FILLED at bid
   - Non-marketable orders → REJECTED
   - Edge cases: exact prices, decimal precision

2. **QuoteTest**:
   - Quote validation
   - Null/blank symbol handling
   - Precision handling

3. **FauxnanceQuoteClientTest**:
   - Quote available on first attempt
   - Quote unavailable → QuoteUnavailableException
   - Retry logic success (fail then succeed)
   - Null response handling

#### Service Tests (With Mocks)

4. **TradeExecutionServiceTest** (6 paths):
   - BUY affordable + marketable → FILLED
   - SELL marketable → FILLED
   - Non-marketable → REJECTED
   - Insufficient funds → REJECTED
   - Account suspended → REJECTED
   - Quote unavailable → REJECTED

#### Integration Tests (With Mocked Fauxnance)

5. **OrderPlacedConsumerIntegrationTest**:
   - Complete flow: event → consumer → service → decision
   - A: Quote available + marketable → FILLED
   - B: Quote available + non-marketable → REJECTED
   - C: Quote unavailable → retries → REJECTED
   - Unknown event type is ignored
   - Null orderId is ignored
   - Unknown JSON fields are tolerated

## Precision Handling

Quote prices from Fauxnance may have many decimal places (e.g., 6 decimals). The FauxnanceQuoteClient automatically scales all prices to 2 decimal places using `BigDecimal.setScale(2, RoundingMode.HALF_UP)` to match the Account.balance precision stored in the database.

## Scope & Limitations

### What's Implemented

✅ Kafka consumer for ORDER_PLACED events
✅ Order/Portfolio/Account/Instrument loading from PostgreSQL
✅ Execution-time validation according to Sprint 5 rules
✅ Fauxnance quote client with bounded retries
✅ Pure decision function (no Spring, no DB, no HTTP)
✅ Comprehensive unit and integration tests
✅ Graceful handling of service failures

### What's NOT Implemented (Next Sprint: Settlement)

❌ Database update of order status (FILLED/REJECTED)
❌ Cash debit for BUY orders
❌ Position/holding updates for SELL orders
❌ Transaction creation
❌ Publication of ORDER_FILLED/ORDER_REJECTED events
❌ Two-guard atomic settlement transaction

The ExecutionResult is returned to the consumer for future settlement processing. The architecture supports plugging in settlement logic without modifying execution logic.

## Error Handling

### Quote Unavailability

When Fauxnance is unavailable:
1. Client retries with exponential backoff (100ms → 200ms → 400ms)
2. After 3 retries (4 total attempts), throws `QuoteUnavailableException`
3. Order is resolved as REJECTED with reason: "Quote unavailable after retries"
4. Order remains in NEW status in database (NOT resolved to terminal state)

### Kafka Message Handling

- Unknown event types are silently ignored
- Null/missing orderId values are silently ignored
- Exceptions during processing are logged; Kafka retries based on configuration
- No poison pill DLT mechanism yet (future enhancement)

## Project Structure

```
executor/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/neueda/learning/
│   │   │   ├── ExecutorApplication.java
│   │   │   ├── client/
│   │   │   │   └── FauxnanceQuoteClient.java
│   │   │   ├── config/
│   │   │   │   ├── KafkaConsumerConfig.java
│   │   │   │   └── RestTemplateConfig.java
│   │   │   ├── consumer/
│   │   │   │   └── OrderPlacedConsumer.java
│   │   │   ├── domain/
│   │   │   │   ├── ExecutionDecision.java
│   │   │   │   ├── ExecutionResult.java
│   │   │   │   ├── Quote.java
│   │   │   │   └── enums/
│   │   │   │       └── ExecutionStatus.java
│   │   │   ├── event/
│   │   │   │   └── OrderPlacedEvent.java
│   │   │   ├── exception/
│   │   │   │   └── QuoteUnavailableException.java
│   │   │   ├── mapper/
│   │   │   │   ├── AccountMapper.java
│   │   │   │   ├── InstrumentMapper.java
│   │   │   │   ├── OrderMapper.java
│   │   │   │   └── PortfolioMapper.java
│   │   │   ├── model/
│   │   │   │   ├── AccountRecord.java
│   │   │   │   ├── InstrumentRecord.java
│   │   │   │   ├── OrderRecord.java
│   │   │   │   └── PortfolioRecord.java
│   │   │   └── service/
│   │   │       └── TradeExecutionService.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       ├── java/com/neueda/learning/
│       │   ├── client/
│       │   │   └── FauxnanceQuoteClientTest.java
│       │   ├── consumer/
│       │   │   └── OrderPlacedConsumerIntegrationTest.java
│       │   ├── domain/
│       │   │   ├── ExecutionDecisionTest.java
│       │   │   └── QuoteTest.java
│       │   └── service/
│       │       └── TradeExecutionServiceTest.java
│       └── resources/
│           └── application-test.properties
```

## Key Design Decisions

1. **Pure Decision Function**: ExecutionDecision contains zero Spring, SQL, or HTTP dependencies. It can be tested instantly without infrastructure, supporting TDD and rapid iteration.

2. **Separate Record Classes**: OrderRecord, AccountRecord, etc., are distinct from Sprint 5 domain classes (Order, Account, etc.) to avoid coupling. The executor only needs simple data to make decisions.

3. **Retry Inside Client**: Quote unavailability is a business outcome, not a Kafka error. The client handles retries; the service decides to reject instead of poisoning the message.

4. **No Database Writes**: The executor produces ExecutionResult but does NOT update the database. Settlement logic (next sprint) will handle that atomically.

5. **Decimal Precision**: All prices are scaled to 2 decimals (matching Account.balance) for consistency and to avoid rounding surprises.

6. **Event Tolerance**: The Kafka consumer uses `@JsonAnySetter` to gracefully accept events with extra fields, supporting forwards/backwards compatibility.

## Running the Service

```bash
# 1. Start PostgreSQL, Kafka, and Fauxnance (see docker-compose.yml)
docker-compose up

# 2. Compile and run tests
mvn clean test

# 3. Build the application
mvn clean package

# 4. Run the service
java -jar target/executor-0.0.1-SNAPSHOT.jar
```

## Future Enhancements

- [ ] Settlement service to update orders, accounts, and positions
- [ ] Publication of ORDER_FILLED/ORDER_REJECTED events
- [ ] Dead letter topic for irrecoverable Kafka errors
- [ ] Metrics and monitoring (latency, success rates)
- [ ] Execution logging for audit trail
- [ ] Support for more order types (limit, stop, trailing stop)
- [ ] Integration with risk management rules

