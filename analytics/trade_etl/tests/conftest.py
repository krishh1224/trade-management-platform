"""Shared pytest fixtures for the analytics_etl test suite."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

FIXTURES_DIR = Path(__file__).resolve().parent.parent / "fixtures"


@pytest.fixture
def valid_response() -> dict:
    """Load the clean INFY.NS candle response."""
    return json.loads((FIXTURES_DIR / "candles_valid.json").read_text(encoding="utf-8"))


@pytest.fixture
def valid_response_2() -> dict:
    """Load the clean AAPL candle response."""
    return json.loads((FIXTURES_DIR / "candles_valid_2.json").read_text(encoding="utf-8"))


@pytest.fixture
def malformed_response() -> dict:
    """Load the corrupted BROKEN.NS candle response."""
    return json.loads((FIXTURES_DIR / "candles_malformed.json").read_text(encoding="utf-8"))


@pytest.fixture
def multi_symbol_raw(valid_response, valid_response_2) -> dict:
    """Raw data dict with two clean symbols, as returned by extract()."""
    return {
        "INFY.NS": valid_response,
        "AAPL": valid_response_2,
    }

