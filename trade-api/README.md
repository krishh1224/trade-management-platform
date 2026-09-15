# Fauxnance Trade API (Team 5)

This module is the **Fauxnance** trading service: a Spring Boot application exposing the trading REST API, backed by PostgreSQL via MyBatis. It also contains the pure domain entities and business rules that model the platform's financial and trading concepts.

## Project Structure

The project is structured around Java and Maven, keeping the domain layer (`com.team5.domain`) free of framework and persistence concerns, with the application layers built around it.

### Core Domain Models

The primary domain entities include:

*   **`Account`**: Represents a user's financial account, managing balance and account status.
*   **`Client`**: Represents a user/client of the Fauxnance platform.
*   **`Instrument`**: Represents a financial instrument (e.g., a stock or bond) available for trading.
*   **`Order`**: Represents a trading order placed by a client to buy or sell an instrument.
*   **`Portfolio`**: Represents a client's portfolio, aggregating their various holdings.
*   **`PortfolioHolding`**: Represents a specific quantity of an instrument held within a portfolio.
*   **`Transaction`**: Records financial transactions, such as deposits, withdrawals, or executed trades.

### Enums

Standardized values used across the domain models:
*   `AccountStatus`: ACTIVE, SUSPENDED, CLOSED.
*   `Exchange`: Supported financial exchanges (NSE, BSE).
*   `OrderSide`: BUY or SELL.
*   `OrderStatus`: NEW, FILLED, REJECTED, CANCELLED.
*   `RiskProfile`: Risk tolerance levels for clients (LOW, MODERATE, ADVENTUROUS).
*   `TransactionType`: Types of transactions (BUY, SELL, DEPOSIT, WITHDRAWAL).

### Business Rules & Validation

The domain layer encapsulates critical business logic and validation:
*   **`OrderValidator`**: Contains rules for validating orders before they are processed.
*   **`IdempotencyChecker`**: Ensures operations (like order placement) are idempotent to prevent duplicate processing.
*   Custom Domain Exceptions (`InsufficientFundsException`, `AccountNotActiveException`, etc., in `com.team5.exception`) are used to handle business rule violations cleanly.

### Application Layers

Built on top of the domain layer:
*   **`com.team5.controller`**: REST controllers for account and order endpoints.
*   **`com.team5.service`**: Application services orchestrating domain logic and persistence.
*   **`com.team5.mapper`**: MyBatis mappers (XML in `src/main/resources/mapper`).
*   **`com.team5.auth`**: Spring Security configuration. Every route except `GET /health` requires a valid HMAC-signed JWT (Spring Security OAuth2 resource server). Controllers receive the caller's account via `@AuthenticatedAccountId`, and the services enforce that it matches the account being read or traded. Set `JWT_SECRET` (at least 32 bytes); the `local` profile has a dev-only default, the `platform` profile does not.
*   **`com.team5.dto`**: Request and response payloads for the API.
*   **`com.team5.exception`**: All `DomainException` subclasses (each carrying an error code such as `ACC-404`) and `ApiExceptionHandler`, which maps those codes to HTTP responses.

## Technologies Used

*   **Java 17**: The core programming language.
*   **Spring Boot 3.5**: Web layer, configuration, and dependency injection.
*   **Spring Security (OAuth2 Resource Server)**: JWT bearer-token authentication.
*   **MyBatis + PostgreSQL**: Persistence.
*   **Maven**: Dependency management and build tool.
*   **Jakarta Validation (JSR 380)**: Used for declarative validation on domain entities and request DTOs.
*   **JUnit 5 (Jupiter)**: For unit and slice testing.
*   **JaCoCo**: For measuring and reporting test coverage.

## Building and Testing

To compile the project and run the tests:

```bash
./mvnw clean test
```

To generate a test coverage report (JaCoCo):

```bash
./mvnw clean test jacoco:report
```
The coverage report will be available at `target/site/jacoco/index.html`.

Note that `MyBatisMapperIntegrationTest` requires a running PostgreSQL instance (see `docker-compose.yml` at the repository root); it will fail if no database is reachable.

## API Contract

The OpenAPI description of the service lives in [`contracts/trade-api.yaml`](contracts/trade-api.yaml).
