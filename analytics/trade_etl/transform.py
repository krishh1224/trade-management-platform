from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from typing import Any

import pandas as pd

PRICE_QUANT = Decimal("0.01")


@dataclass(frozen=True)
class TransformResult:
    dim_date: pd.DataFrame
    dim_instrument: pd.DataFrame
    dim_account: pd.DataFrame
    fact_trades: pd.DataFrame


def _quantize_money(value: Any) -> Decimal:
    dec = Decimal(str(value))
    return dec.quantize(PRICE_QUANT, rounding=ROUND_HALF_UP)


def _to_datetime(value: Any) -> datetime:
    ts = pd.Timestamp(value)
    if ts.tzinfo is None:
        return ts.to_pydatetime()
    return ts.tz_convert("UTC").tz_localize(None).to_pydatetime()


def transform_trade_rows(
    rows: list[dict[str, Any]],
    loaded_at: datetime | None = None,
) -> TransformResult:
    loaded_ts = loaded_at or datetime.now(timezone.utc).replace(tzinfo=None)

    if not rows:
        return TransformResult(
            dim_date=pd.DataFrame(
                columns=[
                    "date_key",
                    "full_date",
                    "day",
                    "month",
                    "year",
                    "quarter",
                    "day_of_week",
                    "day_name",
                    "month_name",
                    "is_weekday",
                ]
            ),
            dim_instrument=pd.DataFrame(
                columns=[
                    "instrument_key",
                    "symbol",
                    "name",
                    "asset_class",
                    "currency",
                    "tradable",
                    "loaded_at",
                ]
            ),
            dim_account=pd.DataFrame(
                columns=[
                    "account_key",
                    "account_id",
                    "holder_name",
                    "status",
                    "effective_date",
                    "end_date",
                    "is_current",
                    "source_id",
                    "loaded_at",
                ]
            ),
            fact_trades=pd.DataFrame(
                columns=[
                    "trade_key",
                    "account_key",
                    "instrument_key",
                    "date_key",
                    "side",
                    "quantity",
                    "price",
                    "status",
                    "executed_price",
                    "trade_value",
                    "source_order_id",
                    "created_at",
                    "loaded_at",
                ]
            ),
        )

    normalized: list[dict[str, Any]] = []
    for row in rows:
        created_at = _to_datetime(row["order_created_at"])
        account_created_at = _to_datetime(row["account_created_at"])

        price = _quantize_money(row["price"])
        quantity = int(row["quantity"])
        trade_value = (price * Decimal(quantity)).quantize(PRICE_QUANT, rounding=ROUND_HALF_UP)
        status = str(row["status"]).upper()

        normalized.append(
            {
                "trade_key": int(row["order_id"]),
                "account_key": int(row["account_id"]),
                "instrument_key": int(row["instrument_id"]),
                "date_key": int(created_at.strftime("%Y%m%d")),
                "full_date": created_at.date(),
                "created_at": created_at,
                "side": str(row["side"]).upper(),
                "quantity": quantity,
                "price": price,
                "status": status,
                "executed_price": price if status == "FILLED" else None,
                "trade_value": trade_value,
                "source_order_id": str(row["order_id"]),
                "instrument_symbol": str(row["instrument_symbol"]),
                "instrument_name": str(row["instrument_name"]),
                "asset_class": str(row["asset_class"]),
                "instrument_currency": str(row["instrument_currency"]),
                "tradable": bool(row["tradable"]),
                "account_number": str(row["account_number"]),
                "holder_name": str(row["holder_name"]),
                "account_status": str(row["account_status"]),
                "account_effective_date": account_created_at.date(),
            }
        )

    base_df = pd.DataFrame(normalized)

    dim_date = (
        base_df[["date_key", "full_date"]]
        .drop_duplicates()
        .assign(
            day=lambda d: pd.to_datetime(d["full_date"]).dt.day,
            month=lambda d: pd.to_datetime(d["full_date"]).dt.month,
            year=lambda d: pd.to_datetime(d["full_date"]).dt.year,
            quarter=lambda d: pd.to_datetime(d["full_date"]).dt.quarter,
            day_of_week=lambda d: pd.to_datetime(d["full_date"]).dt.isocalendar().day,
            day_name=lambda d: pd.to_datetime(d["full_date"]).dt.day_name(),
            month_name=lambda d: pd.to_datetime(d["full_date"]).dt.month_name(),
            is_weekday=lambda d: pd.to_datetime(d["full_date"]).dt.dayofweek < 5,
        )
        .sort_values(["date_key"])
        .reset_index(drop=True)
    )

    dim_instrument = (
        base_df[
            [
                "instrument_key",
                "instrument_symbol",
                "instrument_name",
                "asset_class",
                "instrument_currency",
                "tradable",
            ]
        ]
        .drop_duplicates(subset=["instrument_key"])
        .rename(
            columns={
                "instrument_symbol": "symbol",
                "instrument_name": "name",
                "instrument_currency": "currency",
            }
        )
        .assign(loaded_at=loaded_ts)
        .sort_values(["instrument_key"])
        .reset_index(drop=True)
    )

    dim_account = (
        base_df[
            [
                "account_key",
                "account_number",
                "holder_name",
                "account_status",
                "account_effective_date",
            ]
        ]
        .drop_duplicates(subset=["account_key"])
        .rename(
            columns={
                "account_number": "account_id",
                "account_status": "status",
                "account_effective_date": "effective_date",
            }
        )
        .assign(end_date=None, is_current=True, source_id=lambda d: d["account_key"], loaded_at=loaded_ts)
        .sort_values(["account_key"])
        .reset_index(drop=True)
    )

    fact_trades = (
        base_df[
            [
                "trade_key",
                "account_key",
                "instrument_key",
                "date_key",
                "side",
                "quantity",
                "price",
                "status",
                "executed_price",
                "trade_value",
                "source_order_id",
                "created_at",
            ]
        ]
        .assign(loaded_at=loaded_ts)
        .sort_values(["created_at", "trade_key"])
        .reset_index(drop=True)
    )

    return TransformResult(
        dim_date=dim_date,
        dim_instrument=dim_instrument,
        dim_account=dim_account,
        fact_trades=fact_trades,
    )
