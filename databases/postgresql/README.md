# Trade Database

This folder contains the PostgreSQL database schema, migrations, seed data, setup script, and database probes for the trading platform.

## Folder Structure

```text
databases/postgresql/
├── schema.sql                     # baseline schema (formerly V1_schema.sql)
├── seed-data.sql                  # sample data (formerly 001_seed.sql)
├── migrations/                    # V2 onwards, applied in version order
│   ├── V2_add_settlement_columns.sql
│   └── ...
├── seed/
│   └── 002_test_data_executor.sql # extra executor test data (not auto-loaded)
├── scripts/
│   └── apply.sh
└── probes/
```

## Requirements

- PostgreSQL
- `psql`
- Bash
- A PostgreSQL database
- Database connection details in `.env`

## Environment Setup

Create a `.env` file inside the `databases/postgresql/` folder (copy `.env.example`).

Example:

```env
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_password
POSTGRES_DB=trade_management
TARGET_DATABASE=trade_management
```

Do not commit `.env` to Git.

## Database Setup

The database setup is handled by:

```text
scripts/apply.sh
```

Make the script executable:

```bash
chmod +x scripts/apply.sh
```

Then run:

```bash
./scripts/apply.sh
```

The script:

1. Reads the PostgreSQL configuration from `.env`
2. Connects to PostgreSQL
3. Applies `schema.sql`, then each migration in `migrations/` in version order
4. Loads the seed data
5. Stops if any SQL error occurs

## Schema

The main database schema is:

```text
schema.sql
```

The database contains these main tables:

```text
clients
   ↓
accounts
   ↓
portfolio
   ↓
portfolio_holdings
   ↓
instruments

portfolio
   ↓
orders
   ↓
transactions
```

Main entities:

- `clients`
- `accounts`
- `portfolio`
- `instruments`
- `portfolio_holdings`
- `orders`
- `transactions`

## Seed Data

Seed data is stored in:

```text
seed-data.sql
```

It provides sample clients, accounts, portfolios, instruments, holdings, orders, and transactions for testing the database.

## Running SQL Manually

Connect to the database:

```bash
psql -h localhost -p 5432 -U postgres -d trade_management
```

Run a SQL file:

```bash
psql -h localhost -p 5432 -U postgres -d trade_management -f schema.sql
```

Run seed data:

```bash
psql -h localhost -p 5432 -U postgres -d trade_management -f seed-data.sql
```

## Useful PostgreSQL Commands

List databases:

```sql
\l
```

Connect to a database:

```sql
\c trade_management
```

List tables:

```sql
\dt
```

Describe a table:

```sql
\d clients
```

Check sample data:

```sql
SELECT * FROM clients;
```

Check accounts:

```sql
SELECT * FROM accounts;
```

Check orders:

```sql
SELECT * FROM orders;
```

Check transactions:

```sql
SELECT * FROM transactions;
```

Exit PostgreSQL:

```sql
\q
```

## Database Probes

The `probes/` folder contains SQL/scripts used to verify that the database is working correctly.

Typical checks include:

- Table existence
- Foreign-key relationships
- Seed data
- Constraints
- Queries across related tables

## Fresh Database Setup

For a fresh database, the simplest setup is:

```bash
cd databases/postgresql
chmod +x scripts/apply.sh
./scripts/apply.sh
```

This creates the schema and loads the seed data.

## Important Notes

- Keep `.env` local and never commit passwords.
- `schema.sql` contains the initial database schema (formerly `V1_schema.sql`).
- `seed-data.sql` contains sample data (formerly `001_seed.sql`).
- `apply.sh` uses `ON_ERROR_STOP=1`, so SQL errors stop the setup process.
- If the schema changes after `schema.sql` has already been applied to an existing database, add a new migration in `migrations/` (next is `V11_*.sql`) instead of silently changing `schema.sql`.
- The current risk profiles are `LOW`, `MODERATE`, and `ADVENTUROUS`.
- The current account statuses are `ACTIVE`, `SUSPENDED`, and `CLOSED`.
- Orders represent buy/sell requests, while transactions represent completed financial events and cash movements.
