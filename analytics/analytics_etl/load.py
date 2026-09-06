from pathlib import Path
import duckdb
import pandas as pd

def load(clean_data: pd.DataFrame, db_path: str | None = None) -> None:
    store_to_duckdb(clean_data, db_path)

def store_to_duckdb(clean_data: pd.DataFrame, db_path: str | None = None) -> None:
    """Create the DuckDB table if needed and upsert validated rows."""
    target_path = Path(db_path) if db_path else DATABASE_PATH
    target_path.parent.mkdir(parents=True, exist_ok=True)

    con = duckdb.connect(str(target_path))

    try:
        con.execute("""
            CREATE TABLE IF NOT EXISTS stock_prices (
                symbol VARCHAR NOT NULL,
                currency VARCHAR,
                interval VARCHAR NOT NULL,
                date TIMESTAMP NOT NULL,
                open DOUBLE,
                high DOUBLE,
                low DOUBLE,
                close DOUBLE,
                adjclose DOUBLE,
                volume BIGINT,
                synthetic BOOLEAN,
                PRIMARY KEY (symbol, interval, date)
            )
        """)

        con.register("clean_data", clean_data)
        
        # update an existing row with the same key
        con.execute("""
            INSERT INTO stock_prices
            SELECT
                symbol,
                currency,
                interval,
                date,
                open,
                high,
                low,
                close,
                adjclose,
                volume,
                synthetic
            FROM clean_data
            ON CONFLICT (symbol, interval, date)
            DO UPDATE SET
                currency = EXCLUDED.currency,
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                adjclose = EXCLUDED.adjclose,
                volume = EXCLUDED.volume,
                synthetic = EXCLUDED.synthetic
        """)

        print(f"Successfully loaded {len(clean_data)} rows into DuckDB.")
        print(f"Database: {db_path}")

    finally:
        con.close()
        