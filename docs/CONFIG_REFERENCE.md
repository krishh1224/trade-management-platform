# Configuration Reference

Complete documentation of all environment variables used in the Fauxnance Trading Platform.

## Quick Reference

| Variable | Service | Required | Default | Notes |
|----------|---------|----------|---------|-------|
| JWT_SECRET | All | ✅ Yes | None | Min 32 bytes for token signing |
| POSTGRES_PASSWORD | Database | ✅ Yes | None | Database credentials |
| KAFKA_BOOTSTRAP_SERVERS | Kafka | ✅ Yes | None | Broker addresses |
| POSTGRES_HOST | DB | No | localhost | Docker: `postgres` |
| POSTGRES_PORT | DB | No | 5432 | PostgreSQL port |
| POSTGRES_DB | DB | No | trade_management | Database name |
| POSTGRES_USER | DB | No | postgres | Database user |
| PORT | Nest Auth | No | 3000 | Server port |
| SERVER_PORT | Trade APIs | No | 8081/8082 | Java service ports |
| NODE_ENV | Nest Auth | No | development | Environment mode |
| SPRING_PROFILES_ACTIVE | Java | No | local | Spring active profile |

---

## Complete Variable Listing

### ⚙️ AUTHENTICATION & JWT

#### JWT_SECRET
- **Used By**: Nest Auth (generation), Trade API (verification), Executor (verification)
- **Required**: ✅ Yes
- **Type**: String
- **Constraints**: Must be ≥32 bytes (256 bits)
- **Example**: `openssl rand -base64 32`
- **Security**: Generate random value, never commit to git, rotate periodically

#### JWT_ISSUER
- **Used By**: Nest Auth (token generation)
- **Required**: No
- **Default**: `auth-service`
- **Type**: String
- **Constraints**: Used in token `iss` claim for validation

#### ACCESS_TOKEN_TTL_SECONDS
- **Used By**: Nest Auth (token generation)
- **Required**: No
- **Default**: `900` (15 minutes)
- **Type**: Integer
- **Constraints**: Positive integer, recommended 300-1800 seconds
- **Notes**: Shorter = more secure but more refresh requests

#### REFRESH_TOKEN_TTL_SECONDS
- **Used By**: Nest Auth (token generation)
- **Required**: No
- **Default**: `604800` (7 days)
- **Type**: Integer
- **Constraints**: Positive integer, longer than ACCESS_TOKEN_TTL

---

### 🗄️ DATABASE (PostgreSQL)

#### POSTGRES_HOST
- **Used By**: Nest Auth, Trade API, Executor, Analytics
- **Required**: No
- **Default**: `localhost`
- **Type**: String (hostname or IP)
- **Examples**:
  - Docker: `postgres` (Docker service name)
  - Local: `localhost`
  - Remote: `db.example.com`

#### POSTGRES_PORT
- **Used By**: All services with database access
- **Required**: No
- **Default**: `5432`
- **Type**: Integer
- **Range**: 1-65535

#### POSTGRES_DB
- **Used By**: All database-accessing services
- **Required**: No
- **Default**: `trade_management`
- **Type**: String (database name)
- **Notes**: Must match schema in `database/database/migration/V1_schema.sql`

#### POSTGRES_USER
- **Used By**: All database-accessing services
- **Required**: No
- **Default**: `postgres`
- **Type**: String
- **Notes**: Should be restricted user with minimal privileges in production

#### POSTGRES_PASSWORD
- **Used By**: All database-accessing services
- **Required**: ✅ Yes (in production)
- **Default**: `postgres` (development only)
- **Type**: String
- **Security**: Generate random, minimum 16 characters, rotate regularly
- **Environment Variables Derived**:
  - `SPRING_DATASOURCE_PASSWORD` - Java services read from this

#### SPRING_DATASOURCE_USERNAME
- **Used By**: Spring Boot services (Trade API, Executor)
- **Required**: No
- **Default**: `${POSTGRES_USER}`
- **Notes**: Maps to POSTGRES_USER by default

#### SPRING_DATASOURCE_PASSWORD
- **Used By**: Spring Boot services (Trade API, Executor)
- **Required**: No
- **Default**: `${POSTGRES_PASSWORD}`
- **Notes**: Maps to POSTGRES_PASSWORD by default

---

### 📬 KAFKA MESSAGING

#### KAFKA_BOOTSTRAP_SERVERS
- **Used By**: Trade API (producer), Executor (consumer)
- **Required**: ✅ Yes
- **Default**: None (must be explicitly set)
- **Type**: String (comma-separated list of broker addresses)
- **Examples**:
  - Docker: `kafka:29092` (internal Docker network)
  - Local: `localhost:8085` (exposed port)
  - Production: `kafka-broker-1:9092,kafka-broker-2:9092,kafka-broker-3:9092`
- **⚠️ IMPORTANT**: The IP `10.8.75.70:8085` was team-specific and should NOT be used

#### KAFKA_HOST
- **Used By**: Docker Compose (Kafka advertised listener)
- **Required**: No
- **Default**: `10.8.64.174` (team-specific, override as needed)
- **Type**: String (IP or hostname)
- **Notes**: External clients use this address to connect to Kafka

#### KAFKA_ORDERS_TOPIC
- **Used By**: Order placement service topics
- **Required**: No
- **Default**: `orders`
- **Type**: String
- **Notes**: Created by `create-topics.sh` script

#### KAFKA_TRADE_EVENTS_TOPIC
- **Used By**: Trade event publishing
- **Required**: No
- **Default**: `trade-events`
- **Type**: String
- **Notes**: For future use; currently using `orders` topic

#### KAFKA_CONSUMER_GROUP_ID
- **Used By**: Executor (Kafka consumer group)
- **Required**: No
- **Default**: `trade-executor`
- **Type**: String
- **Notes**: Allows multiple executor instances to share consumption

---

### 🔑 NEST AUTH SERVICE

#### PORT
- **Used By**: Nest Auth server
- **Required**: No
- **Default**: `3000`
- **Type**: Integer
- **Range**: 1-65535
- **Docker**: `docker-compose` maps this to port 3000 on host

#### NODE_ENV
- **Used By**: Nest.js runtime
- **Required**: No
- **Default**: `development`
- **Type**: String
- **Valid Values**: `development`, `production`
- **Effects**: Enables/disables debug logging, error page details

#### LOG_LEVEL
- **Used By**: Nest Auth logging
- **Required**: No
- **Default**: `debug`
- **Type**: String
- **Valid Values**: `error`, `warn`, `info`, `debug`

#### BCRYPT_COST
- **Used By**: Password hashing (Nest Auth)
- **Required**: No
- **Default**: `12`
- **Type**: Integer
- **Range**: 4-31
- **⚠️ Performance**: Cost 12 ≈ 270ms per hash; higher = slower
- **Recommendation**: 10-12 for production

#### LOGIN_THROTTLE_ATTEMPTS
- **Used By**: Brute force protection
- **Required**: No
- **Default**: `5`
- **Type**: Integer
- **Notes**: Max login attempts before temporary lock

#### LOGIN_THROTTLE_DURATION_MS
- **Used By**: Brute force protection lockout duration
- **Required**: No
- **Default**: `900000` (15 minutes)
- **Type**: Integer (milliseconds)

#### CORS_ORIGINS
- **Used By**: CORS policy
- **Required**: No
- **Default**: `http://localhost:3000,http://localhost:8081`
- **Type**: String (comma-separated URLs)
- **Production**: Restrict to specific domain(s)

---

### 📊 TRADE API SERVICE

#### SERVER_PORT
- **Used By**: Trade API HTTP server
- **Required**: No
- **Default**: `8081`
- **Type**: Integer
- **Docker**: Exposed as port 8081

#### SPRING_PROFILES_ACTIVE
- **Used By**: Spring Boot profile selection
- **Required**: No
- **Default**: `local`
- **Type**: String
- **Valid Values**: `local`, `platform`, `production`
- **Effects**: Loads corresponding `application-{profile}.properties`

#### AUTH_STUB_BASE_URL
- **Used By**: Trade API (auth service discovery)
- **Required**: No
- **Default**: `http://localhost:3000` (local) or `http://nest-auth:3000` (Docker)
- **Type**: URL
- **Notes**: Changed from old `auth-stub` to new `nest-auth` service

#### FAUXNANCE_API_KEY
- **Used By**: Market data API client
- **Required**: No (legacy service)
- **Type**: String (API key)
- **Notes**: Legacy service for quote data; set to dummy value if unused

#### FAUXNANCE_BASE_URL
- **Used By**: Market data API endpoint
- **Required**: No
- **Default**: `https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1`
- **Type**: URL

---

### 🚀 TRADE EXECUTOR SERVICE

#### SERVER_PORT
- **Used By**: Executor HTTP server
- **Required**: No
- **Default**: `8082`
- **Type**: Integer
- **Docker**: Exposed as port 8082

#### KAFKA_CONSUMER_GROUP_ID
- **Used By**: Executor consumer group
- **Required**: No
- **Default**: `trade-executor`
- **Type**: String
- **Notes**: Allows horizontal scaling of executor instances

#### KAFKA_AUTO_OFFSET_RESET
- **Used By**: Offset handling on consumer start
- **Required**: No
- **Default**: `earliest`
- **Type**: String
- **Valid Values**: `earliest`, `latest`, `none`
- **Notes**: `earliest` = process all historical events

#### KAFKA_ENABLE_AUTO_COMMIT
- **Used By**: Offset commit strategy
- **Required**: No
- **Default**: `false`
- **Type**: Boolean
- **Security**: `false` ensures manual commit after successful processing

#### EXECUTOR_RETRY_INITIAL_INTERVAL_MS
- **Used By**: Exponential backoff for quote service retries
- **Required**: No
- **Default**: `100` (milliseconds)
- **Type**: Integer
- **Notes**: First retry waits 100ms

#### EXECUTOR_RETRY_MAX_INTERVAL_MS
- **Used By**: Maximum backoff delay
- **Required**: No
- **Default**: `5000` (5 seconds)
- **Type**: Integer

#### EXECUTOR_RETRY_MULTIPLIER
- **Used By**: Exponential backoff multiplier
- **Required**: No
- **Default**: `2.0`
- **Type**: Float
- **Calculation**: 100ms × 2.0 = 200ms, 200ms × 2.0 = 400ms, etc.

#### EXECUTOR_RETRY_MAX_ATTEMPTS
- **Used By**: Maximum retry count
- **Required**: No
- **Default**: `3`
- **Type**: Integer
- **Notes**: After 3 failed attempts, order rejected

#### MARKET_DATA_POLL_INTERVAL_SECONDS
- **Used By**: Market data polling frequency
- **Required**: No
- **Default**: `60` (seconds)
- **Type**: Integer
- **Constraints**: Minimum 44 seconds (to stay within 2000 requests/day quota)

#### SETTLEMENT_OPTIMISTIC_LOCK_MAX_RETRIES
- **Used By**: Settlement retries for concurrent updates
- **Required**: No
- **Default**: `3`
- **Type**: Integer
- **Notes**: Retries if another process updates settlement simultaneously

---

### 📈 ANALYTICS ETL

#### DUCKDB_PATH
- **Used By**: Analytics ETL (DuckDB database location)
- **Required**: No
- **Default**: `./analytics/analytics.duckdb`
- **Type**: File path
- **Notes**: Relative to project root; can be absolute path

#### PYTHON_ENV
- **Used By**: Python runtime
- **Required**: No
- **Default**: `development`
- **Type**: String
- **Valid Values**: `development`, `production`

#### ETL_BATCH_SIZE
- **Used By**: ETL pipeline batch processing
- **Required**: No
- **Default**: `1000` (rows)
- **Type**: Integer
- **Notes**: Larger batches = faster but higher memory usage

#### ETL_MAX_RETRIES
- **Used By**: Failed extraction retry count
- **Required**: No
- **Default**: `3`
- **Type**: Integer

#### ETL_REQUEST_TIMEOUT_SECONDS
- **Used By**: HTTP request timeout for extractions
- **Required**: No
- **Default**: `30` (seconds)
- **Type**: Integer

---

### 📝 LOGGING

#### SPRING_LOGGING_LEVEL_ROOT
- **Used By**: Default logging level for all services
- **Required**: No
- **Default**: `INFO`
- **Type**: String
- **Valid Values**: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`

#### SPRING_LOGGING_LEVEL_COM_TEAM5
- **Used By**: Trade API logging level
- **Required**: No
- **Default**: `DEBUG`
- **Type**: String
- **Notes**: Detailed logging for Trade API package

#### SPRING_LOGGING_LEVEL_COM_NEUEDA
- **Used By**: Executor logging level
- **Required**: No
- **Default**: `DEBUG`
- **Type**: String
- **Notes**: Detailed logging for Executor package

#### SPRING_LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_KAFKA
- **Used By**: Kafka client logging
- **Required**: No
- **Default**: `INFO`
- **Type**: String

#### SPRING_LOGGING_LEVEL_ORG_APACHE_KAFKA
- **Used By**: Apache Kafka native logging
- **Required**: No
- **Default**: `WARN`
- **Type**: String

#### ENABLE_CORRELATION_ID
- **Used By**: Request tracing
- **Required**: No
- **Default**: `true`
- **Type**: Boolean
- **Notes**: Enables request correlation for distributed tracing

#### CORRELATION_ID_HEADER
- **Used By**: HTTP header name for correlation ID
- **Required**: No
- **Default**: `X-Correlation-ID`
- **Type**: String

---

## Environment-Specific Configurations

### Local Development
```bash
POSTGRES_HOST=localhost
POSTGRES_PASSWORD=postgres
KAFKA_BOOTSTRAP_SERVERS=localhost:8085
JWT_SECRET=$(openssl rand -base64 32)
```

### Docker Compose
```bash
POSTGRES_HOST=postgres
POSTGRES_PASSWORD=postgres
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
JWT_SECRET=$(openssl rand -base64 32)
```

### Production
```bash
POSTGRES_HOST=prod-db.example.com
POSTGRES_PASSWORD=$(get_secret_from_vault)
KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092,kafka3:9092
JWT_SECRET=$(get_secret_from_vault)
SPRING_PROFILES_ACTIVE=production
NODE_ENV=production
```

---

## Validation Checklist

Before deploying, verify:

- [ ] JWT_SECRET is set and ≥32 bytes
- [ ] POSTGRES_PASSWORD is strong (≥16 chars, mixed case, numbers, symbols)
- [ ] KAFKA_BOOTSTRAP_SERVERS points to accessible broker(s)
- [ ] All REQUIRED variables are set (not using defaults)
- [ ] No hardcoded secrets in code (use env vars only)
- [ ] Logging levels appropriate for environment (DEBUG for dev, INFO+ for prod)
- [ ] Database connection tested: `psql -h $POSTGRES_HOST -U $POSTGRES_USER -d $POSTGRES_DB`
- [ ] Kafka connection tested: `kafka-console-consumer --bootstrap-server $KAFKA_BOOTSTRAP_SERVERS --list-topics`

---

**Last Updated:** September 27, 2026  
**Version:** 1.0.0
