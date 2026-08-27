import json
from pathlib import Path

import pandas as pd
import pytest

from analytics_etl.transform import transform


FIXTURES = Path(__file__).parent.parent / ".cache"


def load_fixture(filename: str):
    with open(FIXTURES / filename, "r") as file:
        return json.load(file)


# ---------------------------------------------------------
# 1. Valid input transforms correctly
# ---------------------------------------------------------

def test_valid_reliance_fixture_transforms():
    raw = load_fixture(
<<<<<<< Updated upstream
        "RELIANCE.NS_2025-08-01_2026-08-26_956f592a.json"
=======
        "C:/Users/Administrator/Desktop/chennai-capstone-SE5-team5/.cache/RELIANCE.NS_2025-08-01_2026-08-26_956f592a.json"
>>>>>>> Stashed changes
    )

    result = transform(raw)

    assert isinstance(result, pd.DataFrame)

<<<<<<< Updated upstream
    assert len(result) == 261
=======
    assert len(result) ==261
>>>>>>> Stashed changes

    assert list(result.columns) == [
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

    assert result["symbol"].unique()[0] == "RELIANCE.NS"

    assert pd.api.types.is_datetime64_any_dtype(
        result["date"]
    )


# ---------------------------------------------------------
# 2. Missing required field rejected
# ---------------------------------------------------------

def test_missing_close_value_is_removed():
    raw = load_fixture(
<<<<<<< Updated upstream
        "TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
=======
        "C:/Users/Administrator/Desktop/chennai-capstone-SE5-team5/.cache/TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
>>>>>>> Stashed changes
    )

    result = transform(raw)

    # 2026-07-02 had no close
    assert not (
        result["date"]
        == pd.Timestamp("2026-07-02")
    ).any()


# ---------------------------------------------------------
# 3. Invalid numeric values removed
# ---------------------------------------------------------

def test_non_numeric_open_value_is_removed():
    raw = load_fixture(
<<<<<<< Updated upstream
        "TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
=======
        "C:/Users/Administrator/Desktop/chennai-capstone-SE5-team5/.cache/TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
>>>>>>> Stashed changes
    )

    result = transform(raw)

    # 2026-07-06 contained open="n/a"
    assert not (
        result["date"]
        == pd.Timestamp("2026-07-06")
    ).any()


# ---------------------------------------------------------
# 4. High below low rejected
# ---------------------------------------------------------

def test_rejects_high_below_low():
    raw = load_fixture(
<<<<<<< Updated upstream
        "TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
=======
        "C:/Users/Administrator/Desktop/chennai-capstone-SE5-team5/.cache/TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
>>>>>>> Stashed changes
    )

    result = transform(raw)

    # 2026-07-07:
    #
    # high = 168.1
    # low  = 175.85
    #
    # This row must never reach analytics.

    assert not (
        result["date"]
        == pd.Timestamp("2026-07-07")
    ).any()


# ---------------------------------------------------------
# 5. Negative volume rejected
# ---------------------------------------------------------

def test_negative_volume_is_removed():
    raw = load_fixture(
<<<<<<< Updated upstream
        "TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
=======
        "C:/Users/Administrator/Desktop/chennai-capstone-SE5-team5/.cache/TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
>>>>>>> Stashed changes
    )

    result = transform(raw)

    # 2026-07-08 volume=-1

    assert not (
        result["date"]
        == pd.Timestamp("2026-07-08")
    ).any()


# ---------------------------------------------------------
# 6. Empty dataset
# ---------------------------------------------------------

def test_empty_dataset_returns_empty_dataframe():

    raw = {
        "data": {
            "symbol": "TEST.NS",
            "currency": "INR",
            "interval": "1d",
            "candles": []
        }
    }

    result = transform(raw)

    assert isinstance(result, pd.DataFrame)

    assert result.empty


# ---------------------------------------------------------
# 7. Invalid raw input
# ---------------------------------------------------------

def test_invalid_response_type_raises_error():

    with pytest.raises(TypeError):
        transform(None)
        
# ---------------------------------------------------------
# 8. Invalid date format
# ---------------------------------------------------------
def test_non_iso_date():
    raw = load_fixture(
<<<<<<< Updated upstream
        "TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
=======
        "C:/Users/Administrator/Desktop/chennai-capstone-SE5-team5/.cache/TATASTEEL.BO_2025-08-01_2026-08-26_e967c611.json"
>>>>>>> Stashed changes
    )

    result = transform(raw)

    assert (
        result["date"]
        == pd.Timestamp("2026-07-17")
    ).any()