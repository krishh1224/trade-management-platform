# Order Service (trade-api)

## Overview

Order Service is a Spring Boot + MyBatis API for account and trade operations.
It owns order placement/cancellation, balances, positions, preferences, watchlists,
notifications, strategies, and advisor endpoints exposed by the trade API module.

## Tech Stack

- Java 17
- Spring Boot 3.5
- MyBatis
- PostgreSQL
- Kafka (for event publishing/consumption features)
- Maven Wrapper

## Required Configuration

Configuration is loaded from:

- src/main/resources/application.properties
- src/main/resources/application-local.properties (local profile defaults)
- .env or manifest.env (via spring-dotenv)

Set these values before running:

- SPRING_DATASOURCE_URL (default local: jdbc:postgresql://localhost:5432/trade_management)
- SPRING_DATASOURCE_USERNAME (default local: postgres)
- SPRING_DATASOURCE_PASSWORD
- JWT_SECRET (recommended; local has a dev fallback, platform should set real secret)

Commonly needed depending on enabled modules:

- SPRING_KAFKA_BOOTSTRAP_SERVERS (Kafka bootstrap servers)
- FAUXNANCE_API_KEY (market data)
- STRATEGY_TOKEN_SECRET (strategies delegated order token signing)
- GEMINI_API_KEY (advisor module)
- NOTIFICATIONS_EMAIL_ENABLED and MAIL_* (email notifications)
- NOTIFICATIONS_PUSH_ENABLED and PUSH_VAPID_* (browser push notifications)

## Run Commands

From services/order-service:

### Windows

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

### macOS/Linux

```bash
./mvnw clean test
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Build JAR

```bash
./mvnw clean package
```

### Run Packaged JAR

```bash
java -jar target/trade-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

## Health and API Contract

- Health endpoint: GET /health
- OpenAPI contract: ../../contracts/trade-api.yaml
