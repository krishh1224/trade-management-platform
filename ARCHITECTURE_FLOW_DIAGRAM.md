# Full System Architecture and Flow Diagram

This document describes the end-to-end architecture and runtime flow of the Artha trading platform across UI, APIs, asynchronous execution, and analytics.

## System Topology

```mermaid
flowchart LR
  subgraph Users[Users and Clients]
    UI[Angular Trade UI :4200]
    APIClients[Postman / Scripts / External Clients]
  end

  subgraph AuthBoundary[Auth Boundary]
    AUTH[auth-service\nNestJS :3000\nJWT issue and refresh]
  end

  subgraph TradeDomain[Trade Domain]
    ORDERAPI[order-service\nSpring Boot :8081\nAccounts, Orders, Portfolio, Watchlist, Alerts, Advisor]
    PG[(PostgreSQL :5432\naccounts, orders, positions, notifications)]
  end

  subgraph Eventing[Asynchronous Execution]
    KAFKA[(Kafka Broker :29092\nexternal listener :8085)]
    EXEC[executor-service\nSpring Boot :8082\nOrder execution worker]
  end

  subgraph Analytics[Analytics and Reporting]
    ETL[Python ETL pipelines\nExtract, Transform, Load]
    DUCK[(DuckDB analytics marts)]
    REPORTS[Claims reports and charts]
  end

  UI -->|Sign in| AUTH
  APIClients -->|Auth APIs| AUTH
  AUTH -->|JWT access token| UI

  UI -->|Bearer JWT + REST| ORDERAPI
  APIClients -->|REST contracts in contracts/| ORDERAPI

  ORDERAPI -->|Read and write| PG
  ORDERAPI -->|Publish order events| KAFKA

  KAFKA -->|Consume order events| EXEC
  EXEC -->|Read and update order lifecycle| PG

  PG -->|Batch extract| ETL
  ETL -->|Load curated datasets| DUCK
  DUCK --> REPORTS

  ORDERAPI -.->|OpenAPI specs| APIClients
  UI -.->|Advisor chat requests| ORDERAPI
```

## Primary Runtime Flows

1. Authentication
- User signs in through the UI.
- auth-service verifies credentials and returns JWT tokens.
- UI uses the token for protected trade endpoints.

2. Order Placement and Execution
- UI submits an order to order-service.
- order-service validates account, symbol, quantity, and price.
- Order is persisted to PostgreSQL and published to Kafka.
- executor-service consumes event, executes order logic, and updates final status.

3. Analytics Pipeline
- ETL extracts historical trading/account data from PostgreSQL.
- ETL transforms and loads curated datasets into DuckDB marts.
- Reports and charts are generated from DuckDB outputs.

## Ownership Boundaries

- auth-service owns identity and token issuance.
- order-service owns trade-domain APIs and persistence rules.
- executor-service owns asynchronous execution logic.
- ETL owns analytical transformations and reporting artifacts.

## API Contract Sources

OpenAPI contracts are located in contracts/:
- auth-api.yaml
- trade-api.yaml
- portfolio-api.yaml
- watchlists-api.yaml
- strategies-api.yaml
- notifications-api.yaml
- advisor-api.yaml
