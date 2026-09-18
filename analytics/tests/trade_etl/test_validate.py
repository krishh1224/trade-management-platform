from __future__ import annotations

from datetime import datetime
from decimal import Decimal

import pandas as pd

from trade_etl.validate import validate_fact_rows


def _make_fact_df() -> pd.DataFrame:
    return pd.DataFrame(
        [
            {
                "trade_key": 1,
                "account_key": 101,
                "instrument_key": 301,
                "date_key": 20260801,
                "side": "BUY",
                "quantity": 10,
                "price": Decimal("100.00"),
                "status": "FILLED",
                "executed_price": Decimal("100.00"),
                "trade_value": Decimal("1000.00"),
                "source_order_id": "501",
                "created_at": datetime(2026, 8, 1, 10, 0, 0),
                "loaded_at": datetime(2026, 8, 1, 10, 1, 0),
            },
            {
                "trade_key": 2,
                "account_key": 999,
                "instrument_key": 301,
                "date_key": 20260801,
                "side": "BUY",
                "quantity": 10,
                "price": Decimal("100.00"),
                "status": "FILLED",
                "executed_price": Decimal("100.00"),
                "trade_value": Decimal("1000.00"),
                "source_order_id": "502",
                "created_at": datetime(2026, 8, 1, 10, 0, 0),
                "loaded_at": datetime(2026, 8, 1, 10, 1, 0),
            },
            {
                "trade_key": 3,
                "account_key": 101,
                "instrument_key": 301,
                "date_key": 20260801,
                "side": "HOLD",
                "quantity": 10,
                "price": Decimal("100.00"),
                "status": "FILLED",
                "executed_price": Decimal("100.00"),
                "trade_value": Decimal("1000.00"),
                "source_order_id": "503",
                "created_at": datetime(2026, 8, 1, 10, 0, 0),
                "loaded_at": datetime(2026, 8, 1, 10, 1, 0),
            },
            {
                "trade_key": 4,
                "account_key": 101,
                "instrument_key": 301,
                "date_key": 20260801,
                "side": "BUY",
                "quantity": -1,
                "price": Decimal("100.00"),
                "status": "FILLED",
                "executed_price": Decimal("100.00"),
                "trade_value": Decimal("-100.00"),
                "source_order_id": "504",
                "created_at": datetime(2026, 8, 1, 10, 0, 0),
                "loaded_at": datetime(2026, 8, 1, 10, 1, 0),
            },
            {
                "trade_key": 5,
                "account_key": 101,
                "instrument_key": 301,
                "date_key": 20260801,
                "side": "BUY",
                "quantity": 1,
                "price": Decimal("0.00"),
                "status": "FILLED",
                "executed_price": Decimal("0.00"),
                "trade_value": Decimal("0.00"),
                "source_order_id": "505",
                "created_at": datetime(2026, 8, 1, 10, 0, 0),
                "loaded_at": datetime(2026, 8, 1, 10, 1, 0),
            },
            {
                "trade_key": 6,
                "account_key": 101,
                "instrument_key": 301,
                "date_key": 20260801,
                "side": "BUY",
                "quantity": 2,
                "price": Decimal("100.00"),
                "status": "PENDING",
                "executed_price": None,
                "trade_value": Decimal("200.00"),
                "source_order_id": "506",
                "created_at": datetime(2026, 8, 1, 10, 0, 0),
                "loaded_at": datetime(2026, 8, 1, 10, 1, 0),
            },
            {
                "trade_key": 7,
                "account_key": 101,
                "instrument_key": 301,
                "date_key": 20260801,
                "side": "BUY",
                "quantity": 2,
                "price": Decimal("100.00"),
                "status": "FILLED",
                "executed_price": Decimal("100.00"),
                "trade_value": Decimal("150.00"),
                "source_order_id": "507",
                "created_at": datetime(2026, 8, 1, 10, 0, 0),
                "loaded_at": datetime(2026, 8, 1, 10, 1, 0),
            },
        ]
    )


def test_validate_facts_separates_valid_and_dead_letters() -> None:
    result = validate_fact_rows(
        _make_fact_df(),
        existing_account_keys={101},
        existing_instrument_keys={301},
        existing_date_keys={20260801},
        batch_id="batch-1",
    )

    assert len(result.valid_facts) == 1
    assert len(result.dead_letters) == 6

    reasons = "|".join(result.dead_letters["reason"].tolist())
    assert "missing_account_dimension" in reasons
    assert "invalid_side" in reasons
    assert "quantity_must_be_positive" in reasons
    assert "price_must_be_positive" in reasons
    assert "invalid_status" in reasons
    assert "trade_value_mismatch" in reasons
