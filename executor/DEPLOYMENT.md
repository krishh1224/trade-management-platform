# Trade Executor - Deployment & Operations Guide

## Prerequisites

- Java 17 or later
- PostgreSQL 12+
- Apache Kafka 3.0+
- Maven 3.6+
- Fauxnance service running on `http://localhost:8081`

## Database Setup

The executor expects the following tables in PostgreSQL:

### Orders Table
```sql
CREATE TABLE orders (
    order_id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL,
    instrument_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    price NUMERIC(19, 6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    side VARCHAR(10) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    FOREIGN KEY (portfolio_id) REFERENCES portfolios(portfolio_id),
    FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
);
```

### Portfolios Table
```sql
CREATE TABLE portfolios (
    portfolio_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (account_id) REFERENCES accounts(acc_id)
);
```

### Accounts Table
```sql
CREATE TABLE accounts (
    acc_id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL,
    account_number CHAR(10) NOT NULL UNIQUE,
    risk_profile VARCHAR(20) NOT NULL,
    base_currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### Instruments Table
```sql
CREATE TABLE instruments (
    instrument_id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    asset_class VARCHAR(50) NOT NULL,
    currency CHAR(3) NOT NULL,
    exchange VARCHAR(50) NOT NULL,
    tradable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

## Kafka Setup

### Create Topic
```bash
# Using kafka-topics.sh (adjust broker address as needed)
kafka-topics.sh --create \
  --topic orders \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=86400000
```

### Consumer Group
The service creates the consumer group automatically:
- **Group**: `trade-executor`
- **Topic**: `orders`

## Building

```bash
cd executor

# Clean, compile, and run tests
mvn clean test

# Build the package
mvn clean package -DskipTests

# Output: target/executor-0.0.1-SNAPSHOT.jar
```

## Configuration

### Environment Variables (Optional)

Override properties via environment variables:

```bash
# Database
export SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.com:5432/trading
export SPRING_DATASOURCE_USERNAME=trade_user
export SPRING_DATASOURCE_PASSWORD=secure_password

# Kafka
export SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092
export SPRING_KAFKA_CONSUMER_GROUP_ID=trade-executor

# Fauxnance
export FAUXNANCE_BASE_URL=https://quotes.fauxnance.com
export FAUXNANCE_QUOTE_MAX_RETRIES=5
export FAUXNANCE_QUOTE_INITIAL_BACKOFF_MS=200
export FAUXNANCE_QUOTE_MAX_BACKOFF_MS=5000
```

### Configuration File

Edit `application.properties` before building:

```properties
spring.datasource.url=jdbc:postgresql://prod-db:5432/trading_platform
spring.datasource.username=executor_user
spring.datasource.password=${DB_PASSWORD}

spring.kafka.bootstrap-servers=kafka-cluster:9092

fauxnance.base-url=https://api.fauxnance.com
fauxnance.quote.max-retries=3
```

## Running the Service

### Development
```bash
mvn spring-boot:run
```

### Production
```bash
java -jar executor-0.0.1-SNAPSHOT.jar \
  --spring.datasource.username=executor_user \
  --spring.datasource.password=${DB_PASSWORD} \
  --spring.kafka.bootstrap-servers=kafka-broker:9092
```

### Docker (if image exists)
```bash
docker run -e SPRING_DATASOURCE_PASSWORD=secret \
           -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
           -p 8082:8082 \
           executor:0.0.1
```

## Health Check

The service runs on port 8082. There's no dedicated health endpoint yet, but you can monitor:

```bash
# Check application logs
docker logs executor

# Verify it's listening
netstat -tlnp | grep 8082

# Kafka consumer group status
kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group trade-executor \
  --describe
```

## Monitoring & Logging

### Log Levels

Adjust in `application.properties`:

```properties
# All executor logs at DEBUG
logging.level.com.neueda.learning=DEBUG

# Kafka logs at WARNING (reduces noise)
logging.level.org.springframework.kafka=INFO
logging.level.org.apache.kafka=WARN

# Database access logs (verbose)
logging.level.org.mybatis=DEBUG
```

### Key Events to Monitor

1. **Order Processing**
   - `INFO: Processing ORDER_PLACED event for orderId: {}`
   - `INFO: Execution decision for order {}: {}`

2. **Failures**
   - `WARN: Order {} not found in database`
   - `ERROR: Quote unavailable for {} after {} retries`
   - `ERROR: Account {} cannot afford BUY order {}`
   - `ERROR: Unexpected error executing order {}`

3. **Quote Service**
   - `DEBUG: Fetching quote for {} (attempt {}/{})`
   - `DEBUG: Quote retrieved for {}: {}`
   - `WARN: Failed to fetch quote for {} (attempt {}/{})`

## Testing in Production

### Publish a Test Order Placed Event

```bash
# Using kafka console producer
echo '{"eventType":"ORDER_PLACED","orderId":123,"accountId":456,"timestamp":1694768400000}' | \
  kafka-console-producer.sh \
    --broker-list localhost:9092 \
    --topic orders \
    --property "key.serializer=org.apache.kafka.common.serialization.StringSerializer" \
    --property "parse.key=true" \
    --property "key.separator=:" <<< "456:{'eventType':'ORDER_PLACED','orderId':123,'accountId':456,'timestamp':$(date +%s)000}"
```

### Check Database for Results

```sql
-- View the order (should still be NEW, no settlement yet)
SELECT * FROM orders WHERE order_id = 123;

-- Check account balance (should not change yet)
SELECT balance FROM accounts WHERE acc_id = 456;
```

## Troubleshooting

### Issue: Service won't start
```
Error: Cannot connect to PostgreSQL at jdbc:postgresql://localhost:5432/trading_platform
```
**Solution**: Verify PostgreSQL is running and accessible
```bash
psql -h localhost -U postgres -d trading_platform -c "SELECT 1;"
```

### Issue: Kafka messages not being consumed
```
WARN: Consumer group trade-executor not finding messages
```
**Solution**: Check topic exists and bootstrap servers are correct
```bash
kafka-topics.sh --list --bootstrap-server localhost:9092
kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group trade-executor --describe
```

### Issue: Quote service failing
```
ERROR: Quote unavailable for INFY.NS after 3 retries
```
**Solution**: Verify Fauxnance is running
```bash
curl http://localhost:8081/quotes/INFY.NS
```

### Issue: Slow order processing
```
Order execution is taking > 5 seconds
```
**Solution**: Enable DEBUG logging to identify bottleneck
- Check quote client retry settings (may be retrying too many times)
- Verify database query performance
- Check Kafka broker latency

## Database Maintenance

### Analyzing Query Performance

```sql
-- Check order queries
EXPLAIN ANALYZE SELECT * FROM orders WHERE order_id = 123;

-- Check account queries
EXPLAIN ANALYZE SELECT * FROM accounts WHERE acc_id = 456;
```

### Connection Pool Monitoring

```bash
# Monitor active connections
watch -n 1 'psql -c "SELECT count(*) FROM pg_stat_activity WHERE datname = '\''trading_platform'\'';" -t'
```

## Graceful Shutdown

The service respects Kubernetes and Docker graceful shutdown signals:

```bash
# Send SIGTERM (Spring Boot waits for in-flight messages)
kill -TERM <pid>

# Force shutdown after timeout
kill -9 <pid>
```

Kafka will rebalance the consumer group and another instance will pick up where it left off.

## Scaling Considerations

### Horizontal Scaling (Multiple Instances)

1. Increase Kafka topic partitions (if needed)
2. Start multiple instances with the same consumer group `trade-executor`
3. Kafka automatically distributes partitions across instances

```bash
# Start instance 1
java -jar executor-0.0.1-SNAPSHOT.jar --server.port=8082

# In another terminal, start instance 2
java -jar executor-0.0.1-SNAPSHOT.jar --server.port=8083
```

### Performance Tuning

```properties
# Increase batch size for efficiency
spring.kafka.consumer.max.poll.records=1000

# Fine-tune fetch sizes
spring.kafka.consumer.fetch.min.bytes=1024
spring.kafka.consumer.fetch.max.wait.ms=500

# Database connection pooling
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

## What's Next

After this service is deployed and validated:

1. **Settlement Service** - Processes ExecutionResult and updates database
2. **Event Publication** - Publishes ORDER_FILLED/ORDER_REJECTED events
3. **Monitoring Dashboard** - Track execution latency, success rates
4. **Performance Optimization** - Batch processing, caching, etc.

## Support

For issues beyond this guide:
1. Check the README.md for architecture overview
2. Review raw logs in `executor/target/logs/` (if configured)
3. Run the test suite to verify service works: `mvn test`
4. Check Kafka broker logs: Docker logs or `/var/log/kafka/`
5. Verify database connectivity: `SELECT * FROM orders LIMIT 1;`

