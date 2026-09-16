from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

import duckdb
import pandas as pd

PIPELINE_WATERMARK_TABLE = "etl_watermark"
DEAD_LETTER_TABLE = "fact_trade_dead_letter"


def open_duckdb(db_path: str | Path) -> duckdb.DuckDBPyConnection:
    target = Path(db_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    return duckdb.connect(str(target))


def initialize_schema(
    connection: duckdb.DuckDBPyConnection,
    schema_sql_path: str | Path,
) -> None:
    schema = Path(schema_sql_path).read_text(encoding="utf-8")
    schema = schema.replace("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ")
    connection.execute(schema)


def ensure_etl_tables(connection: duckdb.DuckDBPyConnection) -> None:
    connection.execute(
        f"""
        CREATE TABLE IF NOT EXISTS {PIPELINE_WATERMARK_TABLE} (
            pipeline_name VARCHAR PRIMARY KEY,
            last_created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL
        )
        """
    )

    connection.execute(
        f"""
        CREATE TABLE IF NOT EXISTS {DEAD_LETTER_TABLE} (
            batch_id VARCHAR NOT NULL,
            source_order_id VARCHAR,
            reason VARCHAR NOT NULL,
            payload_json VARCHAR NOT NULL,
            created_at TIMESTAMP NOT NULL
        )
        """
    )


def get_watermark(
    connection: duckdb.DuckDBPyConnection,
    pipeline_name: str,
) -> datetime | None:
    row = connection.execute(
        f"""
        SELECT last_created_at
        FROM {PIPELINE_WATERMARK_TABLE}
        WHERE pipeline_name = ?
        """,
        [pipeline_name],
    ).fetchone()
    if not row:
        return None
    return row[0]


def update_watermark(
    connection: duckdb.DuckDBPyConnection,
    pipeline_name: str,
    last_created_at: datetime,
) -> None:
    updated_at = datetime.now(timezone.utc).replace(tzinfo=None)
    connection.execute(
        f"""
        INSERT INTO {PIPELINE_WATERMARK_TABLE} (pipeline_name, last_created_at, updated_at)
        VALUES (?, ?, ?)
        ON CONFLICT (pipeline_name)
        DO UPDATE SET
            last_created_at = EXCLUDED.last_created_at,
            updated_at = EXCLUDED.updated_at
        """,
        [pipeline_name, last_created_at, updated_at],
    )


def load_dimensions(
    connection: duckdb.DuckDBPyConnection,
    dim_date: pd.DataFrame,
    dim_instrument: pd.DataFrame,
    dim_account: pd.DataFrame,
) -> None:
    if not dim_date.empty:
        connection.register("tmp_dim_date", dim_date)
        connection.execute(
            """
            INSERT INTO dim_date (
                date_key, full_date, day, month, year, quarter,
                day_of_week, day_name, month_name, is_weekday
            )
            SELECT
                date_key, full_date, day, month, year, quarter,
                day_of_week, day_name, month_name, is_weekday
            FROM tmp_dim_date
            ON CONFLICT (date_key)
            DO UPDATE SET
                full_date = EXCLUDED.full_date,
                day = EXCLUDED.day,
                month = EXCLUDED.month,
                year = EXCLUDED.year,
                quarter = EXCLUDED.quarter,
                day_of_week = EXCLUDED.day_of_week,
                day_name = EXCLUDED.day_name,
                month_name = EXCLUDED.month_name,
                is_weekday = EXCLUDED.is_weekday
            """
        )

    if not dim_instrument.empty:
        connection.register("tmp_dim_instrument", dim_instrument)
        connection.execute(
            """
            INSERT INTO dim_instrument (
                instrument_key, symbol, name, asset_class, currency,
                exchange, tradable, loaded_at
            )
            SELECT
                instrument_key, symbol, name, asset_class, currency,
                exchange, tradable, loaded_at
            FROM tmp_dim_instrument
            ON CONFLICT (instrument_key)
            DO UPDATE SET
                symbol = EXCLUDED.symbol,
                name = EXCLUDED.name,
                asset_class = EXCLUDED.asset_class,
                currency = EXCLUDED.currency,
                exchange = EXCLUDED.exchange,
                tradable = EXCLUDED.tradable,
                loaded_at = EXCLUDED.loaded_at
            """
        )

    if not dim_account.empty:
        connection.register("tmp_dim_account", dim_account)
        connection.execute(
            """
            INSERT INTO dim_account (
                account_key, account_id, holder_name, status,
                effective_date, end_date, is_current, source_id, loaded_at
            )
            SELECT
                account_key, account_id, holder_name, status,
                effective_date, end_date, is_current, source_id, loaded_at
            FROM tmp_dim_account
            ON CONFLICT (account_key)
            DO UPDATE SET
                account_id = EXCLUDED.account_id,
                holder_name = EXCLUDED.holder_name,
                status = EXCLUDED.status,
                effective_date = EXCLUDED.effective_date,
                end_date = EXCLUDED.end_date,
                is_current = EXCLUDED.is_current,
                source_id = EXCLUDED.source_id,
                loaded_at = EXCLUDED.loaded_at
            """
        )


def get_dimension_key_sets(
    connection: duckdb.DuckDBPyConnection,
) -> tuple[set[int], set[int], set[int]]:
    account_keys = {
        int(row[0])
        for row in connection.execute("SELECT account_key FROM dim_account").fetchall()
    }
    instrument_keys = {
        int(row[0])
        for row in connection.execute("SELECT instrument_key FROM dim_instrument").fetchall()
    }
    date_keys = {
        int(row[0])
        for row in connection.execute("SELECT date_key FROM dim_date").fetchall()
    }
    return account_keys, instrument_keys, date_keys


def load_facts(connection: duckdb.DuckDBPyConnection, fact_trades: pd.DataFrame) -> None:
    if fact_trades.empty:
        return

    connection.register("tmp_fact_trades", fact_trades)
    connection.execute(
        """
        INSERT INTO fact_trades (
            trade_key, account_key, instrument_key, date_key,
            side, quantity, price, status, executed_price,
            trade_value, source_order_id, created_at, loaded_at
        )
        SELECT
            trade_key, account_key, instrument_key, date_key,
            side, quantity, price, status, executed_price,
            trade_value, source_order_id, created_at, loaded_at
        FROM tmp_fact_trades
        ON CONFLICT (source_order_id)
        DO UPDATE SET
            trade_key = EXCLUDED.trade_key,
            account_key = EXCLUDED.account_key,
            instrument_key = EXCLUDED.instrument_key,
            date_key = EXCLUDED.date_key,
            side = EXCLUDED.side,
            quantity = EXCLUDED.quantity,
            price = EXCLUDED.price,
            status = EXCLUDED.status,
            executed_price = EXCLUDED.executed_price,
            trade_value = EXCLUDED.trade_value,
            created_at = EXCLUDED.created_at,
            loaded_at = EXCLUDED.loaded_at
        """
    )


def load_dead_letters(
    connection: duckdb.DuckDBPyConnection,
    dead_letters: pd.DataFrame,
) -> None:
    if dead_letters.empty:
        return

    connection.register("tmp_fact_dead_letters", dead_letters)
    connection.execute(
        f"""
        INSERT INTO {DEAD_LETTER_TABLE} (
            batch_id,
            source_order_id,
            reason,
            payload_json,
            created_at
        )
        SELECT
            batch_id,
            source_order_id,
            reason,
            payload_json,
            created_at
        FROM tmp_fact_dead_letters
        """
    )
