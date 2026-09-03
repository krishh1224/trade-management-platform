# Analytics ETL

This folder contains the analytics pipeline for extracting market data, transforming it, and loading it into DuckDB.

## Folder Structure

```text
analytics/
├── analytics_etl/
│   ├── __init__.py
│   ├── client.py
│   ├── extract.py
│   ├── transform.py
│   └── load.py
├── contracts/
│   └── analytics-schema.sql
├── fixtures/
├── scripts/
│   └── setup_analytics_schema.py
├── tests/
├── analytics.duckdb
├── claims.md
├── compute_claims.py
├── pyproject.toml
├── run_extract.py
└── README.md
```

## Requirements

- Python 3.10+
- DuckDB
- Fauxnance API access
- `FAUXNANCE_API_KEY`

## Setup

Create and activate a virtual environment:

### Windows

```bash
python -m venv .venv
.venv\Scripts\activate
```

### macOS / Linux

```bash
python3 -m venv .venv
source .venv/bin/activate
```

Install dependencies:

```bash
pip install -e .
```

## Environment Variable

Create a `.env` file inside the `analytics/` folder:

```env
FAUXNANCE_API_KEY=your_api_key_here
```

Do not commit `.env` to Git.

## DuckDB

The pipeline uses:

```text
analytics.duckdb
```

`load.py` creates `analytics.duckdb` if it does not already exist.

The database contains:

```text
analytics.duckdb
├── stock_prices
├── dim_account
├── dim_instrument
├── dim_date
└── fact_trades
```

The existing `stock_prices` table is preserved.

## Analytical Schema

The analytical schema is defined in:

```text
contracts/analytics-schema.sql
```

It contains:

- `dim_account`
- `dim_instrument`
- `dim_date`
- `fact_trades`

Create the analytical tables using:

```bash
python scripts/setup_analytics_schema.py
```

This opens the existing `analytics.duckdb` database and creates the analytical tables without removing `stock_prices`.

## Running the ETL

Run the ETL pipeline with:

```bash
python run_extract.py
```

Pipeline flow:

```text
Fauxnance API
     ↓
Extract
     ↓
Transform
     ↓
Load
     ↓
analytics.duckdb
```

## Running Tests

Run all tests:

```bash
pytest
```

Run with verbose output:

```bash
pytest -v
```

## Business Claims

Business claims are stored in:

```text
claims.md
```

Run the claims computation with:

```bash
python compute_claims.py
```

## Common Commands

Run these commands from inside the `analytics/` directory:

```bash
# Install dependencies
pip install -e .

# Create analytical tables
python scripts/setup_analytics_schema.py

# Run ETL
python run_extract.py

# Run tests
pytest

# Run tests with verbose output
pytest -v

# Compute business claims
python compute_claims.py
```

## Important Notes

- Keep `.env` local and never commit API keys.
- `analytics.duckdb` is the analytical database.
- `stock_prices` must be preserved.
- `contracts/analytics-schema.sql` contains the analytical table definitions.
- Run the schema setup before loading analytical tables into a fresh database.
