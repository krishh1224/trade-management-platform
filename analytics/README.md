# Analytics ETL

This folder contains two isolated pipelines:

1. `analytics_etl` (legacy market-data ETL) -> `analytics.duckdb`
2. `trade_etl` (Sprint 7 trade ETL) -> `sprint7_analytics.duckdb`

## Quick Start

Run these commands from inside `analytics/`.

### Windows

```bash
python -m venv .venv
.venv\Scripts\activate
python -m pip install -r requirements.txt
```

### macOS / Linux

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
```

Create a `.env` file with the variables you need:

```env
# Legacy market ETL
FAUXNANCE_API_KEY=your_api_key_here

# Sprint 7 trade ETL
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=trade_management
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Optional for Sprint 7 output path
SPRINT7_DUCKDB_PATH=sprint7_analytics.duckdb
```

Do not commit real secrets.

## Pipeline Overview

### Legacy Market ETL (`analytics_etl`)

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

### Sprint 7 Trade ETL (`trade_etl`)

```text
PostgreSQL
     ↓
Extract (incremental by orders.created_at watermark)
     ↓
Transform
     ↓
Validate
     ↓
Load dimensions then facts
     ↓
sprint7_analytics.duckdb
```

## Legacy Market ETL Commands

Run legacy ETL:

```bash
python run_extract.py
python -m analytics_etl.pipeline
```

Create analytical schema tables in `analytics.duckdb`:

```bash
python scripts/setup_analytics_schema.py
```

## Sprint 7 Trade ETL Commands

Run Sprint 7 ETL:

```bash
python -m trade_etl
trade-etl-run
```

Run twice to validate incremental behavior:

```bash
python -m trade_etl
python -m trade_etl
```

## Verify DuckDB Outputs

Verify Sprint 7 counts and watermark:

```bash
python -c "import duckdb; con=duckdb.connect('sprint7_analytics.duckdb'); print('fact_trades', con.execute('select count(*) from fact_trades').fetchone()[0]); print('dim_date', con.execute('select count(*) from dim_date').fetchone()[0]); print('dim_instrument', con.execute('select count(*) from dim_instrument').fetchone()[0]); print('dim_account', con.execute('select count(*) from dim_account').fetchone()[0]); print('dead_letters', con.execute('select count(*) from fact_trade_dead_letter').fetchone()[0]); print('watermark', con.execute('select pipeline_name,last_created_at from etl_watermark').fetchall())"
```

## Tests

Run all tests:

```bash
pytest
pytest -v
```

Run only Sprint 7 trade ETL tests:

```bash
pytest tests/trade_etl -q
pytest tests/trade_etl -v
```

## Business Claims

```bash
python compute_claims.py
```

## Analytical Schema

Schema contract is in:

```text
contracts/analytics-schema.sql
```

Tables defined there:

- `dim_account`
- `dim_instrument`
- `dim_date`
- `fact_trades`

## Folder Structure

```text
analytics/
├── analytics_etl/
│   ├── __init__.py
│   ├── client.py
│   ├── extract.py
│   ├── transform.py
│   └── load.py
├── trade_etl/
│   ├── __init__.py
│   ├── __main__.py
│   ├── extract.py
│   ├── transform.py
│   ├── validate.py
│   ├── load.py
│   └── pipeline.py
├── contracts/
│   └── analytics-schema.sql
├── docs/
│   └── trade-etl-sprint7.md
├── fixtures/
├── scripts/
│   └── setup_analytics_schema.py
├── tests/
│   ├── test_transform.py
│   └── trade_etl/
├── analytics.duckdb
├── sprint7_analytics.duckdb
├── claims.md
├── compute_claims.py
├── pyproject.toml
├── requirements.txt
├── run_extract.py
└── README.md
```

## Common Commands

```bash
# Install dependencies
pip install -e .

# Legacy ETL
python run_extract.py
python -m analytics_etl.pipeline

# Sprint 7 ETL
python -m trade_etl

# Tests
pytest
pytest tests/trade_etl -q

# Business claims
python compute_claims.py
```

## Important Notes

- Keep `.env` local and never commit API keys or credentials.
- Legacy ETL writes to `analytics.duckdb`; Sprint 7 ETL writes to `sprint7_analytics.duckdb`.
- `stock_prices` in `analytics.duckdb` must be preserved.
- Run `scripts/setup_analytics_schema.py` before loading analytical tables into a fresh legacy database.
