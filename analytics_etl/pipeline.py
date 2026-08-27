"""Pipeline orchestrator – wires extract → transform → load.

This module does *nothing else*.  It imports the three step functions,
calls them in order, and provides a CLI entry point.
"""

from __future__ import annotations

import logging
import sys
from pathlib import Path

from dotenv import load_dotenv

from analytics_etl.extract import extract
from analytics_etl.transform import transform
from analytics_etl.load import load

logger = logging.getLogger(__name__)

# ── Configuration ────────────────────────────────────────────────────────

# Symbol universe – at least two NSE/BSE instruments
SYMBOLS: list[str] = [
    "INFY.NS",        # Infosys – NSE
    "RELIANCE.NS",    # Reliance Industries – NSE
    "TATASTEEL.BO",   # Tata Steel – BSE
    "AAPL",           # Apple – US
    "MSFT",           # Microsoft – US
    "X:BTC-USD",      # Bitcoin – Crypto
]

# Date range for candle extraction (roughly the last year)
DEFAULT_START = "2025-08-01"
DEFAULT_END = "2026-08-26"

# DuckDB file for the analytical store
DEFAULT_DB_PATH = Path("analytics.duckdb")


# ── Pipeline ─────────────────────────────────────────────────────────────

def run_pipeline(
    symbols: list[str] = SYMBOLS,
    start: str = DEFAULT_START,
    end: str = DEFAULT_END,
    db_path: Path = DEFAULT_DB_PATH,
):
    """Execute the full extract → transform → load pipeline.

    Parameters
    ----------
    symbols:
        Ticker symbols to pull from Fauxnance.
    start:
        ISO-8601 start date (inclusive).
    end:
        ISO-8601 end date (inclusive).
    db_path:
        Path to the DuckDB analytical store file.

    Returns
    -------
    pandas.DataFrame
        The cleaned DataFrame that was loaded, useful for downstream
        dashboard generation.
    """
    logger.info(
        "Pipeline starting – %d symbols, range %s → %s",
        len(symbols), start, end,
    )

    # Step 1: Extract ──────────────────────────────────────────────────────
    logger.info("Step 1/3: Extract")
    raw_data = extract(symbols, start=start, end=end)
    logger.info(
        "Extraction complete – got data for %d symbol(s): %s",
        len(raw_data), ", ".join(raw_data.keys()),
    )

    # Step 2: Transform ────────────────────────────────────────────────────
    logger.info("Step 2/3: Transform")
    
    import pandas as pd
    
    frames = []
    for sym, raw_resp in raw_data.items():
        try:
            df_sym = transform(raw_resp)
            if not df_sym.empty:
                frames.append(df_sym)
        except Exception as e:
            logger.error("Failed to transform %s: %s", sym, e)
            
    if frames:
        cleaned_df = pd.concat(frames, ignore_index=True)
    else:
        cleaned_df = pd.DataFrame()
        
    logger.info(
        "Transform complete – %d rows, %d columns",
        len(cleaned_df), len(cleaned_df.columns),
    )

    # Step 3: Load ─────────────────────────────────────────────────────────
    logger.info("Step 3/3: Load")
    load(cleaned_df, db_path=str(db_path))
    logger.info("Load complete – data written to %s", db_path)

    return cleaned_df


# ── CLI entry point ──────────────────────────────────────────────────────

def main() -> None:
    """Entry point for ``etl-run`` console script and ``python -m analytics_etl``."""
    # Load .env before anything else
    load_dotenv()

    # Configure logging so every step is visible
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
        datefmt="%H:%M:%S",
    )

    try:
        df = run_pipeline()
        print(
            f"\n[OK] Pipeline finished successfully.\n"
            f"  Rows loaded : {len(df)}\n"
            f"  Symbols     : {', '.join(SYMBOLS)}\n"
            f"  Range       : {DEFAULT_START} -> {DEFAULT_END}\n"
            f"  Store       : {DEFAULT_DB_PATH}\n"
        )
    except KeyboardInterrupt:
        print("\nPipeline interrupted by user.")
        sys.exit(130)
    except Exception as exc:
        logger.error("Pipeline failed: %s", exc, exc_info=True)
        sys.exit(1)


if __name__ == "__main__":
    main()
    
    

