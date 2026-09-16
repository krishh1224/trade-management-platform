# Sprint 7 Trade ETL Implementation Notes

## Summary

A separate Sprint 7 ETL pipeline was added to extract trade/order data from PostgreSQL and load analytics facts/dimensions into a dedicated DuckDB database:

- Source: PostgreSQL transactional tables
- Pipeline package: `trade_etl`
- Target database: `sprint7_analytics.duckdb`
- Existing legacy package `analytics_etl` was not modified.

## What Was Added

## New package

- `trade_etl/__init__.py`
- `trade_etl/__main__.py`
- `trade_etl/extract.py`
- `trade_etl/transform.py`
- `trade_etl/validate.py`
- `trade_etl/load.py`
- `trade_etl/pipeline.py`

## New tests

- `tests/trade_etl/test_extract.py`
- `tests/trade_etl/test_transform.py`
- `tests/trade_etl/test_validate.py`
- `tests/trade_etl/test_load.py`
- `tests/trade_etl/test_pipeline.py`

## Packaging/dependencies

- Added `psycopg[binary]>=3.2` to `requirements.txt` and `pyproject.toml`
- Added script entry point `trade-etl-run`
- Included `trade_etl*` in setuptools package discovery

## Source Schema Used (PostgreSQL)

Derived from `database/database/schema/001_schema.sql` and trade-api mappers.

Primary tables used:

- `orders`
- `portfolio`
- `accounts`
- `clients`
- `instruments`

Core extraction fields:

- Order keys and metrics: `order_id`, `created_at`, `quantity`, `price`, `status`, `side`
- Instrument attributes: `instrument_id`, `ticker`, `name`, `asset_class`, `currency`, `exchange`, `tradable`
- Account attributes: `account_id` (from `portfolio`), `account_number`, account `status`, account `created_at`, holder name (`clients.name`)

## Target Schema Used (DuckDB)

Contract file: `contracts/analytics-schema.sql`

Tables loaded:

- `dim_date`
- `dim_instrument`
- `dim_account`
- `fact_trades`

Additional ETL metadata tables in Sprint 7 DB:

- `etl_watermark` (incremental checkpoint)
- `fact_trade_dead_letter` (invalid record sink)

## Mapping Logic

## Dimensions

1. `dim_date`
- `date_key`: YYYYMMDD from `orders.created_at`
- `full_date`, day/month/year/quarter/day_name/month_name/weekday flags

2. `dim_instrument`
- `instrument_key` <- `instruments.instrument_id`
- `symbol` <- `instruments.ticker`
- `name`, `asset_class`, `currency`, `exchange`, `tradable`

3. `dim_account`
- `account_key` <- `accounts.acc_id`
- `account_id` <- `accounts.account_number`
- `holder_name` <- `clients.name`
- `status` <- `accounts.status`
- SCD columns populated with current snapshot semantics (`is_current=true`, `end_date=NULL`)

## Fact

`fact_trades` loads one row per source order:

- `trade_key` <- `orders.order_id`
- `account_key`, `instrument_key`, `date_key`
- `side`, `quantity`, `price`, `status`
- `executed_price` = `price` when `status='FILLED'`, otherwise `NULL`
- `trade_value` = `quantity * price`
- `source_order_id` <- string form of `order_id`
- `created_at` <- `orders.created_at`

Upsert key for idempotency:

- `source_order_id` (unique in contract)

## Incremental Watermark

Watermark basis:

- `orders.created_at`

Flow:

1. Read watermark from `etl_watermark`
2. Extract where `orders.created_at > watermark`
3. Load dimensions then facts
4. Write max processed `orders.created_at` back to `etl_watermark`

Result:

- Running pipeline again without new source orders does not duplicate facts.

## Validation Rules

Applied before fact insertion:

- Dimension references must exist (`account_key`, `instrument_key`, `date_key`)
- `quantity > 0`
- `price > 0`
- `side in {BUY, SELL}`
- `status in {NEW, FILLED, REJECTED, CANCELLED}`
- `trade_value == quantity * price` (rounded to 2 decimals)

Invalid records:

- Not loaded into `fact_trades`
- Inserted into `fact_trade_dead_letter`
- Include `batch_id`, `source_order_id`, `reason`, full payload, timestamp
- Batch continues (no full-stop on bad row)

## Notable Fixes Made During Validation

1. PostgreSQL parameter typing fix
- Issue: `could not determine data type of parameter $1` when watermark is NULL
- Fix: explicit cast in SQL predicate (`%(watermark)s::timestamp`)

2. Schema re-run idempotency fix
- Issue: table already exists on repeated runs
- Fix: schema initialization uses `CREATE TABLE IF NOT EXISTS`

## Commands

See full runnable command list in `README.md`.

Key commands:

```powershell
python -m trade_etl
pytest tests/trade_etl -q
```

## Test Coverage Added

- Incremental extraction with watermark parameter behavior
- Transform mapping to all dimensions/facts
- Validation of good rows and dead-letter behavior for bad rows
- Load upsert/dedup behavior
- Watermark read/write behavior
- End-to-end incremental check: second run with unchanged source loads zero new rows

## Scope Boundaries Kept

- No changes were made to the legacy `analytics_etl` package.
- No changes were made to `analytics.duckdb` usage in legacy flow.
- Sprint 7 ETL uses `sprint7_analytics.duckdb` only.
