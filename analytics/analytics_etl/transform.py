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

OUTPUT_COLUMNS = [
    "symbol",
    "currency",
    "interval",
    *REQUIRED_FIELDS,
    "volume",
]


# Transform a raw API response into a clean DataFrame
def transform(raw_response: dict[str, Any]) -> pd.DataFrame:
    # 1. Validate the expected response structure
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

    # Reading metadata
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

    # 2. Handle empty datasets by returning an empty DataFrame with required columns
    if not candles:
        return pd.DataFrame(columns=OUTPUT_COLUMNS)

    # Convert raw candles into a DataFrame (List -> DataFrame)
    df = pd.DataFrame(candles)

    # Add metadata to every candle to help describe it
    df["symbol"] = symbol
    df["currency"] = currency
    df["interval"] = interval

    # 3. Ensure required columns exist
    for column in REQUIRED_FIELDS:
        if column not in df.columns:
            df[column] = pd.NA

    # 4. Volume is optional
    if "volume" not in df.columns:
        df["volume"] = pd.NA

    # 5. Parse and standardise dates
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

        # Fall back to DD/MM/YYYY format
        return pd.to_datetime(
            value,
            format="%d/%m/%Y",
            errors="coerce",
        )

    df["date"] = df["date"].apply(parse_date)

    # 6. Convert numeric fields - handle invalid values by converting to NaN
    for column in NUMERIC_PRICE_COLUMNS:
        df[column] = pd.to_numeric(
            df[column],
            errors="coerce",
        )

    df["volume"] = pd.to_numeric(
        df["volume"],
        errors="coerce",
    )

    # 7. Drop rows missing required values (except volume)
    df = df.dropna(subset=REQUIRED_FIELDS)

    # 8. Remove duplicate trading days and keep the first valid occurrence
    df = df.drop_duplicates(
        subset=["symbol", "date"],
        keep="first",
    )

    # 9. Validate ranges and financial consistency

    # (i) Price cannot be negative
    price_is_valid = pd.Series(True, index=df.index)

    for column in NUMERIC_PRICE_COLUMNS:
        price_is_valid &= df[column] >= 0

    # (ii) High must not be below low
    high_low_valid = df["high"] >= df["low"]

    # (iii) Volume may be missing, but cannot be negative
    volume_valid = df["volume"].isna() | (df["volume"] >= 0)

    df = df[
        price_is_valid
        & high_low_valid
        & volume_valid
    ]

    # Sort and reset index
    df = df[OUTPUT_COLUMNS].copy()

    df = df.sort_values(
        by=["symbol", "date"],
    ).reset_index(drop=True)

    return df