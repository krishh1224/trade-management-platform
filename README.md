# Trade Database

This folder contains the PostgreSQL database schema, migrations, seed data, setup script, and database probes for the trading platform.

## Folder Structure

```text
trade_database/
├── database/
│   ├── migrations/
│   │   └── V1_schema.sql
│   ├── schema/
│   │   └── schema.sql
│   └── seed/
│       └── 001_seed.sql
│
├── infra/
│   └── postgres/
│       └── apply.sh
│
└── probes/
```

## Requirements

- PostgreSQL
- `psql`
- Bash
- A PostgreSQL database
- Database connection details in `.env`

## Environment Setup

Create a `.env` file inside the `trade_database/` folder.

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
infra/postgres/apply.sh
```

Make the script executable:

```bash
chmod +x infra/postgres/apply.sh
```

Then run:

```bash
./infra/postgres/apply.sh
```

The script:

1. Reads the PostgreSQL configuration from `.env`
2. Connects to PostgreSQL
3. Applies the database schema
4. Loads the seed data
5. Stops if any SQL error occurs

## Schema

The main database schema is:

```text
database/migrations/V1_schema.sql
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
database/seed/001_seed.sql
```

It provides sample clients, accounts, portfolios, instruments, holdings, orders, and transactions for testing the database.

## Running SQL Manually

Connect to the database:

```bash
psql -h localhost -p 5432 -U postgres -d trade_management
```

Run a SQL file:

```bash
psql -h localhost -p 5432 -U postgres -d trade_management -f database/migrations/V1_schema.sql
```

Run seed data:

```bash
psql -h localhost -p 5432 -U postgres -d trade_management -f database/seed/001_seed.sql
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
cd trade_database
chmod +x infra/postgres/apply.sh
./infra/postgres/apply.sh
```

This creates the schema and loads the seed data.

## Important Notes

- Keep `.env` local and never commit passwords.
- `V1_schema.sql` contains the initial database schema.
- `001_seed.sql` contains sample data.
- `apply.sh` uses `ON_ERROR_STOP=1`, so SQL errors stop the setup process.
- If the schema changes after V1 has already been applied to an existing database, create a new migration such as `V2_*.sql` instead of silently changing V1.
- The current risk profiles are `LOW`, `MODERATE`, and `ADVENTUROUS`.
- The current account statuses are `ACTIVE`, `SUSPENDED`, and `CLOSED`.
- Orders represent buy/sell requests, while transactions represent completed financial events and cash movements.
