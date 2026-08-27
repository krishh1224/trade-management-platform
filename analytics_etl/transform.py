from __future__ import annotations

from typing import Any

import pandas as pd


REQUIRED_FIELDS = [
    "date",
    "open",
    "high",
    "low",
    "close",
    "adjclose",
    "synthetic",
]

NUMERIC_PRICE_COLUMNS = [
    "open",
    "high",
    "low",
    "close",
    "adjclose",
]


def transform(raw_response: dict[str, Any]) -> pd.DataFrame:
    """
    Transform a raw Fauxnance CandlesResponse into a clean pandas DataFrame.

    This function:
    - performs no network calls
    - reads no environment variables
    - writes no files or databases

    Invalid candle rows are dropped.
    Missing volume is retained as a missing value.
    """

    # ---------------------------------------------------------
    # 1. Validate the expected response structure
    # ---------------------------------------------------------
    if not isinstance(raw_response, dict):
        raise TypeError("raw_response must be a dictionary")

    data = raw_response.get("data")

    if not isinstance(data, dict):
        raise ValueError("Response is missing a valid 'data' object")

    candles = data.get("candles", [])

    if candles is None:
        candles = []

    if not isinstance(candles, list):
        raise ValueError("'candles' must be a list")

    # ---------------------------------------------------------
    # 2. Read metadata
    # ---------------------------------------------------------
    symbol = data.get("symbol")
    currency = data.get("currency")
    interval = data.get("interval")

    # Canonicalise casing
    if isinstance(symbol, str):
        symbol = symbol.upper()

    if isinstance(currency, str):
        currency = currency.upper()

    if isinstance(interval, str):
        interval = interval.lower()

    # ---------------------------------------------------------
    # 3. Handle empty datasets
    # ---------------------------------------------------------
    output_columns = [
        "symbol",
        "currency",
        "interval",
        "date",
        "open",
        "high",
        "low",
        "close",
        "adjclose",
        "volume",
        "synthetic",
    ]

    if not candles:
        return pd.DataFrame(columns=output_columns)

    # ---------------------------------------------------------
    # 4. Convert raw candles into a DataFrame
    # ---------------------------------------------------------
    df = pd.DataFrame(candles)

    # Add metadata to every candle
    df["symbol"] = symbol
    df["currency"] = currency
    df["interval"] = interval

    # ---------------------------------------------------------
    # 5. Make sure required columns exist
    # ---------------------------------------------------------
    for column in REQUIRED_FIELDS:
        if column not in df.columns:
            df[column] = pd.NA

    # Volume is optional
    if "volume" not in df.columns:
        df["volume"] = pd.NA

    # ---------------------------------------------------------
    # 6. Parse and standardise dates
    #
    # Supports:
    # YYYY-MM-DD
    # DD/MM/YYYY
    # ---------------------------------------------------------
    def parse_date(value: Any) -> pd.Timestamp | pd.NaT:
        if pd.isna(value):
            return pd.NaT

        if not isinstance(value, str):
            return pd.NaT

        value = value.strip()

        # Standard ISO format
        parsed = pd.to_datetime(
            value,
            format="%Y-%m-%d",
            errors="coerce",
        )

        if not pd.isna(parsed):
            return parsed

        # Resolve DD/MM/YYYY ambiguity using the dataset context
        return pd.to_datetime(
            value,
            format="%d/%m/%Y",
            errors="coerce",
        )

    df["date"] = df["date"].apply(parse_date)

    # ---------------------------------------------------------
    # 7. Convert numeric fields
    #
    # Invalid values such as "n/a" become NaN
    # ---------------------------------------------------------
    for column in NUMERIC_PRICE_COLUMNS:
        df[column] = pd.to_numeric(
            df[column],
            errors="coerce",
        )

    df["volume"] = pd.to_numeric(
        df["volume"],
        errors="coerce",
    )

    # ---------------------------------------------------------
    # 8. Drop rows missing required values
    #
    # Missing volume is allowed.
    # ---------------------------------------------------------
    required_after_conversion = [
        "date",
        "open",
        "high",
        "low",
        "close",
        "adjclose",
        "synthetic",
    ]

    df = df.dropna(subset=required_after_conversion)

    # ---------------------------------------------------------
    # 9. Remove duplicate trading days
    #
    # Keep the first valid occurrence for each symbol/date.
    # ---------------------------------------------------------
    df = df.drop_duplicates(
        subset=["symbol", "date"],
        keep="first",
    )

    # ---------------------------------------------------------
    # 10. Validate ranges and financial consistency
    # ---------------------------------------------------------

    # Prices cannot be negative
    price_is_valid = pd.Series(True, index=df.index)

    for column in NUMERIC_PRICE_COLUMNS:
        price_is_valid &= df[column] >= 0

    # High must not be below low
    high_low_valid = df["high"] >= df["low"]

    # Volume may be missing, but cannot be negative
    volume_valid = df["volume"].isna() | (df["volume"] >= 0)

    df = df[
        price_is_valid
        & high_low_valid
        & volume_valid
    ]

    # ---------------------------------------------------------
    # 11. Final column order and clean index
    # ---------------------------------------------------------
    df = df[output_columns].copy()

    df = df.sort_values(
        by=["symbol", "date"],
    ).reset_index(drop=True)

    return df