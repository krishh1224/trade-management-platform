from __future__ import annotations

from datetime import datetime

from trade_etl.transform import transform_trade_rows


def test_transform_maps_source_to_dimensions_and_fact() -> None:
    rows = [
        {
            "order_id": 501,
            "order_created_at": datetime(2026, 8, 1, 9, 15, 0),
            "quantity": 20,
            "price": "1500.50",
            "status": "FILLED",
            "side": "BUY",
            "instrument_id": 301,
            "instrument_symbol": "INFY.NS",
            "instrument_name": "Infosys Limited",
            "asset_class": "EQUITY",
            "instrument_currency": "INR",
            "exchange": "NSE",
            "tradable": True,
            "account_id": 101,
            "account_number": "1000000001",
            "account_status": "ACTIVE",
            "account_created_at": datetime(2025, 1, 1, 0, 0, 0),
            "holder_name": "Sreyansh Baid",
        },
        {
            "order_id": 505,
            "order_created_at": datetime(2026, 8, 1, 10, 45, 0),
            "quantity": 10,
            "price": "1700.00",
            "status": "REJECTED",
            "side": "BUY",
            "instrument_id": 304,
            "instrument_symbol": "HDFCBANK.NS",
            "instrument_name": "HDFC Bank Limited",
            "asset_class": "EQUITY",
            "instrument_currency": "INR",
            "exchange": "NSE",
            "tradable": True,
            "account_id": 102,
            "account_number": "1000000002",
            "account_status": "ACTIVE",
            "account_created_at": datetime(2025, 1, 1, 0, 0, 0),
            "holder_name": "Riya Rangwani",
        },
    ]

    result = transform_trade_rows(rows)

    assert len(result.dim_date) == 1
    assert len(result.dim_instrument) == 2
    assert len(result.dim_account) == 2
    assert len(result.fact_trades) == 2

    filled_row = result.fact_trades[result.fact_trades["source_order_id"] == "501"].iloc[0]
    rejected_row = result.fact_trades[result.fact_trades["source_order_id"] == "505"].iloc[0]

    assert str(filled_row["executed_price"]) == "1500.50"
    assert rejected_row["executed_price"] is None
    assert str(filled_row["trade_value"]) == "30010.00"
