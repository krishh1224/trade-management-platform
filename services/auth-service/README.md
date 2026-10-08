# Auth Service

## Overview

Auth Service is a NestJS service responsible for authentication and token lifecycle:

- register
- login
- refresh
- me

It issues JWT access tokens and validates runtime config at startup.

## Tech Stack

- Node.js 20+
- NestJS
- TypeScript
- PostgreSQL (credentials/account lookup)

## Required Configuration

Configuration is validated by src/config/env.validation.ts.
Use .env.example as a starting point.

Required (service fails fast if invalid/missing):

- JWT_SECRET (minimum 32 bytes)
- JWT_ISSUER (must be auth-service)
- ACCESS_TOKEN_TTL_SECONDS (must be 900)
- REFRESH_TOKEN_TTL_DAYS (must be 7)
- POSTGRES_DB
- POSTGRES_USER
- POSTGRES_PASSWORD

Optional with defaults:

- PORT (default 3000)
- POSTGRES_HOST (default localhost)
- POSTGRES_PORT (default 5432)
- LOGIN_MAX_ATTEMPTS (default 5)
- LOGIN_LOCKOUT_DURATION_SECONDS (default 60)

## Run Commands

From services/auth-service:

### Setup

```bash
cp .env.example .env
npm ci
```

### Development

```bash
npm run start:dev
```

### Build and Run

```bash
npm run build
npm start
```

### Tests

```bash
npm test
```

## Endpoints

- POST /auth/register
- POST /auth/login
- POST /auth/refresh
- GET /auth/me
- GET /health

Contract source: ../../contracts/auth-api.yaml
