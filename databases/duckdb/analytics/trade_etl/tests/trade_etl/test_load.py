from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from pathlib import Path

import pandas as pd

from trade_etl.load import (
    ensure_etl_tables,
    get_dimension_key_sets,
    get_watermark,
    initialize_schema,
    load_dead_letters,
    load_dimensions,
    load_facts,
    open_duckdb,
    update_watermark,
)


def _schema_path() -> Path:
    return Path(__file__).resolve().parents[2] / "contracts" / "analytics-schema.sql"


def _sample_dims_and_fact() -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    dim_date = pd.DataFrame(
        [
            {
                "date_key": 20260801,
                "full_date": datetime(2026, 8, 1).date(),
                "day": 1,
                "month": 8,
                "year": 2026,
                "quarter": 3,
                "day_of_week": 6,
                "day_name": "Saturday",
                "month_name": "August",
                "is_weekday": False,
            }
        ]
    )
    dim_instrument = pd.DataFrame(
        [
            {
                "instrument_key": 301,
                "symbol": "INFY.NS",
                "name": "Infosys Limited",
                "asset_class": "EQUITY",
                "currency": "INR",
                "tradable": True,
                "loaded_at": datetime(2026, 8, 1, 10, 0, 0),
            }
        ]
    )
    dim_account = pd.DataFrame(
        [
            {
                "account_key": 101,
                "account_id": "1000000001",
                "holder_name": "Sreyansh Baid",
                "status": "ACTIVE",
                "effective_date": datetime(2025, 1, 1).date(),
                "end_date": None,
                "is_current": True,
                "source_id": 101,
                "loaded_at": datetime(2026, 8, 1, 10, 0, 0),
            }
        ]
    )
    fact = pd.DataFrame(
        [
            {
                "trade_key": 501,
                "account_key": 101,
                "instrument_key": 301,
                "date_key": 20260801,
                "side": "BUY",
                "quantity": 20,
                "price": Decimal("1500.50"),
                "status": "FILLED",
                "executed_price": Decimal("1500.50"),
                "trade_value": Decimal("30010.00"),
                "source_order_id": "501",
                "created_at": datetime(2026, 8, 1, 9, 15, 0),
                "loaded_at": datetime(2026, 8, 1, 10, 0, 0),
            }
        ]
    )
    return dim_date, dim_instrument, dim_account, fact


def test_dimension_and_fact_load_with_upsert_and_no_duplicates(tmp_path: Path) -> None:
    db_path = tmp_path / "sprint7_analytics.duckdb"

    dim_date, dim_instrument, dim_account, fact = _sample_dims_and_fact()

    with open_duckdb(db_path) as con:
        initialize_schema(con, _schema_path())
        ensure_etl_tables(con)

        load_dimensions(con, dim_date, dim_instrument, dim_account)
        account_keys, instrument_keys, date_keys = get_dimension_key_sets(con)
        assert account_keys == {101}
        assert instrument_keys == {301}
        assert date_keys == {20260801}

        load_facts(con, fact)
        load_facts(con, fact)

        fact_count = con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
        assert fact_count == 1


def test_watermark_roundtrip_and_dead_letter_insert(tmp_path: Path) -> None:
    db_path = tmp_path / "sprint7_analytics.duckdb"

    with open_duckdb(db_path) as con:
        initialize_schema(con, _schema_path())
        ensure_etl_tables(con)

        assert get_watermark(con, "trade_etl_orders_created_at") is None

        wm = datetime(2026, 8, 2, 10, 0, 0)
        update_watermark(con, "trade_etl_orders_created_at", wm)

        assert get_watermark(con, "trade_etl_orders_created_at") == wm

        dead_letters = pd.DataFrame(
            [
                {
                    "batch_id": "batch-123",
                    "source_order_id": "9001",
                    "reason": "invalid_side",
                    "payload_json": "{\"source_order_id\":\"9001\"}",
                    "created_at": datetime(2026, 8, 2, 10, 1, 0),
                }
            ]
        )
        load_dead_letters(con, dead_letters)

        dlq_count = con.execute("SELECT COUNT(*) FROM fact_trade_dead_letter").fetchone()[0]
        assert dlq_count == 1
