# Executor Service

## Overview

Executor Service is a Spring Boot worker that consumes order events from Kafka,
fetches required state from PostgreSQL, gets market quotes, and decides whether
a NEW order should be FILLED or REJECTED.

It is execution-focused; settlement side effects are handled by downstream flow.

## Tech Stack

- Java 17
- Spring Boot 3.5
- Spring Kafka
- MyBatis
- PostgreSQL
- Maven Wrapper

## Required Configuration

Configuration comes from:

- src/main/resources/application.properties
- src/main/resources/application-local.properties
- .env (via spring-dotenv)

Set these before running:

- SPRING_DATASOURCE_URL (default local: jdbc:postgresql://localhost:5432/trade_management)
- SPRING_DATASOURCE_USERNAME (default local: postgres)
- SPRING_DATASOURCE_PASSWORD
- SPRING_KAFKA_BOOTSTRAP_SERVERS (required; no meaningful default)
- FAUXNANCE_API_KEY (required by quote client)
- JWT_SECRET (validated at startup via auth.jwt.shared-secret)

Optional tuning:

- MARKET_DATA_POLL_INTERVAL_SECONDS (default 60)
- executor.retry.* and fauxnance.quote.* settings in application.properties

## Run Commands

From services/executor-service:

### Windows

```powershell
.\mvnw.cmd clean test "-Djacoco.skip=true"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### macOS/Linux

```bash
./mvnw clean test -Djacoco.skip=true
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Build JAR

```bash
./mvnw clean package
```

### Run Packaged JAR

```bash
java -jar target/executor-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

## Kafka Contract

- Consumes: orders topic (ORDER_PLACED envelope)
- Consumer group: trade-executor
- Publishes trade-event outcomes through configured producer path
