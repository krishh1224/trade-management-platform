# Fauxnance Trading Platform

A sophisticated **microservices-based trading and analytics platform** built as a capstone project by Team 5. Enables users to buy/sell financial instruments through a REST API, processes orders asynchronously via Kafka event streaming, and provides real-time analytics via Python ETL.

## 🚀 Quick Start (5 minutes)

### Prerequisites
- **Docker Desktop** (includes Docker & Docker Compose)
- **Git**
- Java 17, Node.js 20+, Python 3.10+ (for local development without Docker)

### Fastest Setup: Docker

```bash
# 1. Clone repository
git clone <repo-url>
cd chennai-capstone-SE5-team5

# 2. Create environment file
cp .env.example .env
# Edit .env and set JWT_SECRET (minimum 32 bytes) and POSTGRES_PASSWORD

# 3. Start all services (Kafka, PostgreSQL, Auth, Trade API)
docker-compose --profile platform up -d --build

# 4. Create Kafka topics
./create-topics.sh

# 5. Verify services are running
docker-compose ps
# Expected: all services showing "healthy"
```

### Verify Installation

```bash
# Health checks
curl http://localhost:3000/health          # Nest Auth
curl http://localhost:8081/health          # Trade API
curl http://localhost:8082/health          # Trade Executor

# List Kafka topics
docker-compose exec kafka kafka-topics.sh --list --bootstrap-server kafka:29092

# Kafka UI
open http://localhost:8086
```

### Local Development Setup (without Docker)

See [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)

---

## 🏗️ Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│                  Client Applications                      │
└────────────────────────┬─────────────────────────────────┘
                         │
                  ┌──────▼──────┐
                  │ Nest Auth   │ JWT token generation
                  │ (Node.js)   │ (register, login, refresh)
                  │ :3000       │
                  └──────┬──────┘
                         │
                  ┌──────▼──────────────┐
                  │   Trade API         │ REST endpoints
                  │   (Java/Spring)     │ (accounts, orders)
                  │   :8081             │
                  └──────┬──────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
   ┌────▼─────┐  ┌───────▼────────┐  ┌───▼────────────┐
   │ Kafka    │  │ PostgreSQL DB  │  │ Domain Models  │
   │ :8085    │  │ :5432          │  │ (Validation)   │
   │ (orders) │  │                │  │                │
   └────┬─────┘  └────────────────┘  └────────────────┘
        │
        ▼
   ┌────────────────────┐
   │ Trade Executor     │ Consumes events
   │ (Java/Spring)      │ Executes trades
   │ :8082              │ Gets quotes
   └────────┬───────────┘
            │
    ┌───────▼──────────┐
    │ Analytics ETL    │ Extracts/transforms
    │ (Python DuckDB)  │ trade data
    └──────────────────┘
```

### Service Communication Flow

1. **Authentication**: Client → Nest Auth → JWT Token
2. **Order Placement**: Client + JWT → Trade API → Validates → PostgreSQL
3. **Event Publishing**: Trade API → Kafka `orders` topic → Event
4. **Execution**: Trade Executor → Consumes event → Loads order → Gets quote → Executes (FILLED/REJECTED)
5. **Analytics**: PostgreSQL → Analytics ETL → DuckDB → Reports

---

## 📦 Services Overview

| Service | Tech Stack | Purpose | Port | Docs |
|---------|-----------|---------|------|------|
| **Nest Auth** | Node.js, NestJS, TypeScript | JWT token generation & verification | 3000 | [nest-auth/README.md](nest-auth/README.md) |
| **Trade API** | Java 17, Spring Boot 3.5, MyBatis | REST API for trading (accounts, orders, positions) | 8081 | [trade-api/README.md](trade-api/README.md) |
| **Trade Executor** | Java 17, Spring Boot 3.5, Kafka | Kafka consumer for asynchronous order execution | 8082 | [executor/README.md](executor/README.md) |
| **PostgreSQL** | PostgreSQL 16 | Primary persistent database (accounts, orders, positions) | 5432 | [database/README.md](database/README.md) |
| **Analytics ETL** | Python 3.10+, DuckDB | Extract/transform/load for analytics reporting | - | [analytics/README.md](analytics/README.md) |
| **Kafka** | Apache Kafka 3.8.0 | Event streaming broker for async order processing | 8085 | [design/kafka.md](design/kafka.md) |

---

## 🔧 Configuration

### Environment Variables

Create `.env` file in the project root. See [.env.example](.env.example) for complete reference.

**Critical Variables:**
- `JWT_SECRET` - Minimum 32 bytes for JWT signing/verification
- `POSTGRES_PASSWORD` - Database password
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker address
- `POSTGRES_DB` - Database name (default: trade_management)
- `POSTGRES_USER` - Database user (default: postgres)

### Configuration Files

Each service has environment-specific configs:
- `application.properties` - Local development defaults
- `application-platform.properties` - Docker Compose deployment
- `.env` - Runtime environment variables (created from .env.example)

See [docs/CONFIG_REFERENCE.md](docs/CONFIG_REFERENCE.md) for complete environment variable documentation.

---

## 🧪 Testing

### Run All Tests

```bash
# Java tests (Trade API + Executor)
cd trade-api && ./mvnw clean test
cd executor && ./mvnw clean test

# TypeScript tests (Nest Auth)
cd nest-auth && npm ci && npm test

# Python tests (Analytics)
cd analytics && pytest -v
```

### Test Coverage

```bash
# Java coverage
cd trade-api && ./mvnw clean test jacoco:report
# Report: target/site/jacoco/index.html

cd executor && ./mvnw clean test jacoco:report
# Report: target/site/jacoco/index.html

# Node coverage
cd nest-auth && npm test -- --coverage
```

### Integration Tests

Docker Compose-based end-to-end testing:

```bash
docker-compose --profile platform up -d --build
./create-topics.sh
# Run integration tests (see integration-tests/ directory)
```

---

## 📚 Documentation

- **[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)** - High-level architecture & design
- **[docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)** - Local development setup guide
- **[docs/CONFIG_REFERENCE.md](docs/CONFIG_REFERENCE.md)** - Complete environment variable reference
- **[docs/DEPLOYMENT_PRODUCTION.md](docs/DEPLOYMENT_PRODUCTION.md)** - Production deployment guide
- **[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)** - Common issues & solutions
- **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** - Detailed architecture & sequence diagrams
- **[docs/adr/](docs/adr/)** - Architecture Decision Records

### Service Documentation
- [nest-auth/README.md](nest-auth/README.md) - Auth service setup & API
- [trade-api/README.md](trade-api/README.md) - Trade API setup & design
- [executor/README.md](executor/README.md) - Executor setup & implementation
- [analytics/README.md](analytics/README.md) - Analytics ETL setup
- [database/README.md](database/README.md) - Database schema & migrations

---

## 🛠️ Development

### Tech Stack Summary
- **Languages:** Java 17, Node.js 20+, Python 3.10+
- **Frameworks:** Spring Boot 3.5, NestJS, Flask (analytics)
- **Databases:** PostgreSQL 16 (relational), DuckDB (analytics warehouse)
- **Message Broker:** Apache Kafka 3.8.0
- **Authentication:** JWT (HS256)
- **Containerization:** Docker & Docker Compose

### Build from Source

```bash
# Java services
cd trade-api && ./mvnw clean package
cd executor && ./mvnw clean package

# Node service
cd nest-auth && npm ci && npm run build

# Python service
cd analytics && pip install -r requirements.txt
```

### Common Commands

```bash
# View logs
docker-compose logs -f nest-auth          # Follow auth logs
docker-compose logs trade-api --tail=50   # Last 50 lines of trade-api

# Stop all services
docker-compose --profile platform down

# Remove all data (volumes)
docker-compose --profile platform down -v

# Rebuild specific service
docker-compose --profile platform build --no-cache trade-api
```

---

## 🔐 Security Features

✅ **JWT Authentication** - HS256-signed tokens with explicit claim set  
✅ **Password Security** - Bcrypt cost 12 (~270ms per hash)  
✅ **Input Validation** - Validated at every layer  
✅ **Idempotency** - Orders use idempotency keys to prevent duplicates  
✅ **Error Handling** - No sensitive data in error responses  
✅ **SQL Injection Protection** - MyBatis prepared statements  
✅ **CORS Configuration** - Explicit cross-origin policy  

---

## 📊 Monitoring & Health Checks

### Health Check Endpoints

All services expose health check endpoints:

```bash
# Nest Auth
GET http://localhost:3000/health

# Trade API  
GET http://localhost:8081/health

# Trade Executor
GET http://localhost:8082/health

# Kafka UI
http://localhost:8086
```

### Logging

- **Java services:** INFO level; set `SPRING_LOGGING_LEVEL_ROOT=DEBUG` for detailed logs
- **Node service:** Structured logging to stdout
- **Python service:** Standard Python logging

---

## 🚀 Deployment

### Docker Compose (Local/Dev)

```bash
docker-compose --profile platform up -d --build
./create-topics.sh
```

### Production Deployment

See [docs/DEPLOYMENT_PRODUCTION.md](docs/DEPLOYMENT_PRODUCTION.md) for:
- Environment setup
- Database migrations
- Kafka topic creation
- Health check verification
- Rollback procedures

---

## 🐛 Troubleshooting

### Common Issues

**Kafka Connection Failed**
- Ensure `KAFKA_BOOTSTRAP_SERVERS` environment variable is set
- Check Kafka container is healthy: `docker-compose ps`

**JWT Secret Invalid**
- Ensure `JWT_SECRET` is set and ≥32 bytes
- Verify it's the same across all services

**Database Connection Failed**
- Verify PostgreSQL container is healthy
- Check `POSTGRES_PASSWORD` matches in `.env`

See [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) for more.

---

## 📋 Project Status

| Component | Status | Notes |
|-----------|--------|-------|
| Nest Auth (JWT service) | ✅ Complete | Sprint 8 - Full implementation |
| Trade API (REST endpoints) | ✅ Complete | All core endpoints working |
| Trade Executor (Kafka consumer) | ✅ Complete | 37+ tests, all paths verified |
| PostgreSQL Database | ✅ Complete | Schema with migrations |
| Analytics ETL | ✅ Complete | Incremental ETL pipeline |
| Docker Compose Setup | ✅ Complete | Full platform deployment |

---

## 👥 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for:
- Code standards
- Pull request process
- Testing requirements
- Commit message format

---

## 📞 Support

- **Documentation:** [docs/](docs/) folder
- **Issues:** Check [TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)
- **Architecture Questions:** See [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)
- **API Documentation:** Trade API OpenAPI spec at `/swagger-ui.html` (when running)

---

## 📝 License

**Last Updated:** September 27, 2026  
**Version:** 1.0.0  
**Maintainers:** SE5-Team 5
