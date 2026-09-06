# Fauxnance Domain Models (Team 5)

This repository contains the pure domain entities and business rules for the **Fauxnance** trading platform. It represents the core of the application, focusing on modeling the financial and trading concepts without any dependencies on external frameworks, databases, or web layers.

## Project Structure

The project is structured around Java and Maven, enforcing a strict separation of concerns by isolating the domain layer. 

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
*   `AccountStatus`: (e.g., ACTIVE, INACTIVE)
*   `Exchange`: Supported financial exchanges.
*   `OrderSide`: BUY or SELL.
*   `OrderStatus`: (e.g., PENDING, EXECUTED, CANCELLED)
*   `RiskProfile`: Risk tolerance levels for clients.
*   `TransactionType`: Types of transactions (DEPOSIT, WITHDRAWAL, etc.).

### Business Rules & Validation

The domain layer encapsulates critical business logic and validation:
*   **`OrderValidator`**: Contains rules for validating orders before they are processed.
*   **`IdempotencyChecker`**: Ensures operations (like order placement) are idempotent to prevent duplicate processing.
*   Custom Domain Exceptions (`InsufficientFundsException`, `AccountNotActiveException`, etc.) are used to handle business rule violations cleanly.

## Technologies Used

*   **Java 17**: The core programming language.
*   **Maven**: Dependency management and build tool.
*   **Jakarta Validation (JSR 380)**: Used for declarative validation on domain entities (via `hibernate-validator` in tests).
*   **JUnit 5 (Jupiter)**: For unit testing the domain logic.
*   **JaCoCo**: For measuring and reporting test coverage.

## Architectural Constraints

To maintain the purity of the domain layer, this project explicitly forbids dependencies on infrastructure or application frameworks. The `maven-enforcer-plugin` is configured to fail the build if any of the following dependencies are introduced:

*   Spring Framework (`org.springframework:*`)
*   Servlets (`jakarta.servlet:*`, `javax.servlet:*`)
*   Databases & ORMs (`org.postgresql:*`, `com.mysql:*`, `com.h2database:*`, `org.mybatis:*`)
*   Connection Pools (`com.zaxxer:HikariCP`, `org.apache.commons:commons-dbcp2`)

## Building and Testing

To compile the project and run the unit tests:

```bash
mvn clean test
```

To generate a test coverage report (JaCoCo):

```bash
mvn clean test jacoco:report
```
The coverage report will be available at `target/site/jacoco/index.html`.

