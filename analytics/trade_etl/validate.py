from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from typing import Any, Iterable

import pandas as pd

PRICE_QUANT = Decimal("0.01")
VALID_SIDES = {"BUY", "SELL"}
VALID_STATUSES = {"NEW", "FILLED", "REJECTED", "CANCELLED"}


@dataclass(frozen=True)
class ValidationResult:
    valid_facts: pd.DataFrame
    dead_letters: pd.DataFrame


def _json_default(value: Any) -> Any:
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, datetime):
        return value.isoformat()
    return value


def _to_decimal(value: Any) -> Decimal:
    return Decimal(str(value)).quantize(PRICE_QUANT, rounding=ROUND_HALF_UP)


def validate_fact_rows(
    fact_df: pd.DataFrame,
    existing_account_keys: Iterable[int],
    existing_instrument_keys: Iterable[int],
    existing_date_keys: Iterable[int],
    batch_id: str,
    valid_statuses: set[str] | None = None,
) -> ValidationResult:
    statuses = valid_statuses or VALID_STATUSES
    account_keys = set(int(v) for v in existing_account_keys)
    instrument_keys = set(int(v) for v in existing_instrument_keys)
    date_keys = set(int(v) for v in existing_date_keys)
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    valid_rows: list[dict[str, Any]] = []
    dead_rows: list[dict[str, Any]] = []

    if fact_df.empty:
        return ValidationResult(valid_facts=fact_df.copy(), dead_letters=pd.DataFrame(columns=["batch_id", "source_order_id", "reason", "payload_json", "created_at"]))

    for record in fact_df.to_dict(orient="records"):
        reasons: list[str] = []

        side = str(record.get("side", "")).upper()
        status = str(record.get("status", "")).upper()

        account_key = int(record.get("account_key"))
        instrument_key = int(record.get("instrument_key"))
        date_key = int(record.get("date_key"))
        quantity = int(record.get("quantity"))
        price = _to_decimal(record.get("price"))
        trade_value = _to_decimal(record.get("trade_value"))

        if account_key not in account_keys:
            reasons.append("missing_account_dimension")
        if instrument_key not in instrument_keys:
            reasons.append("missing_instrument_dimension")
        if date_key not in date_keys:
            reasons.append("missing_date_dimension")
        if quantity <= 0:
            reasons.append("quantity_must_be_positive")
        if price <= 0:
            reasons.append("price_must_be_positive")
        if side not in VALID_SIDES:
            reasons.append("invalid_side")
        if status not in statuses:
            reasons.append("invalid_status")

        expected_trade_value = (price * Decimal(quantity)).quantize(PRICE_QUANT, rounding=ROUND_HALF_UP)
        if trade_value != expected_trade_value:
            reasons.append("trade_value_mismatch")

        if reasons:
            dead_rows.append(
                {
                    "batch_id": batch_id,
                    "source_order_id": str(record.get("source_order_id")),
                    "reason": ";".join(reasons),
                    "payload_json": json.dumps(record, default=_json_default, sort_keys=True),
                    "created_at": now,
                }
            )
        else:
            valid_rows.append(record)

    valid_df = pd.DataFrame(valid_rows, columns=fact_df.columns)
    dead_df = pd.DataFrame(
        dead_rows,
        columns=["batch_id", "source_order_id", "reason", "payload_json", "created_at"],
    )
    return ValidationResult(valid_facts=valid_df, dead_letters=dead_df)
