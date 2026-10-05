from __future__ import annotations

from datetime import datetime
from pathlib import Path

from trade_etl.load import open_duckdb
from trade_etl.pipeline import run_pipeline


def _schema_path() -> Path:
    return Path(__file__).resolve().parents[2] / "contracts" / "analytics-schema.sql"


def _order_rows() -> list[dict]:
    base_row = {
        "order_created_at": datetime(2026, 8, 3, 10, 0, 0),
        "quantity": 20,
        "price": "1500.50",
        "side": "BUY",
        "instrument_id": 301,
        "instrument_symbol": "INFY.NS",
        "instrument_name": "Infosys Limited",
        "asset_class": "EQUITY",
        "instrument_currency": "INR",
        "tradable": True,
        "account_id": 101,
        "account_number": "1000000001",
        "account_status": "ACTIVE",
        "account_created_at": datetime(2025, 1, 1, 0, 0, 0),
        "holder_name": "Sreyansh Baid",
    }
    return [
        {**base_row, "order_id": 701, "status": "FILLED"},
        {**base_row, "order_id": 702, "status": "PENDING"},
    ]


def test_pipeline_incremental_second_run_has_no_duplicates(tmp_path: Path, monkeypatch) -> None:
    db_path = tmp_path / "sprint7_analytics.duckdb"
    schema_path = _schema_path()
    seen_watermarks = []

    class DummyConfig:
        pass

    monkeypatch.setattr("trade_etl.pipeline.PostgresConfig.from_env", lambda: DummyConfig())

    source_rows = _order_rows()

    def fake_extract(_config, watermark):
        seen_watermarks.append(watermark)
        if watermark is None:
            return source_rows
        return []

    monkeypatch.setattr("trade_etl.pipeline.extract_incremental", fake_extract)

    summary1 = run_pipeline(
        duckdb_path=db_path,
        schema_path=schema_path,
        watermark_name="test_watermark",
    )
    summary2 = run_pipeline(
        duckdb_path=db_path,
        schema_path=schema_path,
        watermark_name="test_watermark",
    )

    assert summary1.extracted_rows == 2
    assert summary1.valid_fact_rows == 1
    assert summary1.dead_letter_rows == 1

    assert summary2.extracted_rows == 0
    assert summary2.valid_fact_rows == 0
    assert summary2.dead_letter_rows == 0

    assert seen_watermarks[0] is None
    assert seen_watermarks[1] == summary1.watermark_after

    with open_duckdb(db_path) as con:
        fact_count = con.execute("SELECT COUNT(*) FROM fact_trades").fetchone()[0]
        dead_letter_count = con.execute("SELECT COUNT(*) FROM fact_trade_dead_letter").fetchone()[0]

    assert fact_count == 1
    assert dead_letter_count == 1
