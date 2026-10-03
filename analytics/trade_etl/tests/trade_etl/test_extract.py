from __future__ import annotations

from datetime import datetime

import pytest

from trade_etl.extract import PostgresConfig, extract_incremental


class FakeCursor:
    def __init__(self, rows: list[dict]):
        self.rows = rows
        self.last_query = ""
        self.last_params = {}

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query: str, params: dict) -> None:
        self.last_query = query
        self.last_params = params

    def fetchall(self):
        return self.rows


class FakeConnection:
    def __init__(self, rows: list[dict]):
        self.cursor_obj = FakeCursor(rows)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def cursor(self):
        return self.cursor_obj


def test_extract_incremental_uses_watermark_query() -> None:
    rows = [{"order_id": 1, "order_created_at": datetime(2026, 1, 1, 10, 0, 0)}]
    conn = FakeConnection(rows)

    config = PostgresConfig(
        host="localhost",
        port=5432,
        database="trade_management",
        user="postgres",
        password="postgres",
    )
    watermark = datetime(2026, 1, 1, 0, 0, 0)

    output = extract_incremental(config, watermark, connection_factory=lambda _: conn)

    assert output == rows
    assert "o.created_at >" in conn.cursor_obj.last_query
    assert conn.cursor_obj.last_params["watermark"] == watermark


def test_postgres_config_from_env_requires_values(monkeypatch: pytest.MonkeyPatch) -> None:
    for key in [
        "POSTGRES_HOST",
        "POSTGRES_PORT",
        "POSTGRES_DB",
        "POSTGRES_USER",
        "POSTGRES_PASSWORD",
    ]:
        monkeypatch.delenv(key, raising=False)

    with pytest.raises(ValueError):
        PostgresConfig.from_env()
