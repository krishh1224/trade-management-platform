from __future__ import annotations

import logging
import os
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from dotenv import load_dotenv

from trade_etl.extract import PostgresConfig, extract_incremental
from trade_etl.load import (
    ensure_etl_tables,
    get_dimension_key_sets,
    get_watermark,
    initialize_schema,
    load_dead_letters,
    load_dimensions,
    load_facts,
    open_duckdb,
    update_watermark,
)
from trade_etl.transform import transform_trade_rows
from trade_etl.validate import validate_fact_rows

logger = logging.getLogger(__name__)

ROOT_DIR = Path(__file__).resolve().parents[1]
DEFAULT_DUCKDB_PATH = ROOT_DIR / "sprint7_analytics.duckdb"
DEFAULT_SCHEMA_PATH = ROOT_DIR / "contracts" / "analytics-schema.sql"
DEFAULT_WATERMARK_NAME = "trade_etl_orders_created_at"


@dataclass(frozen=True)
class PipelineSummary:
    batch_id: str
    extracted_rows: int
    valid_fact_rows: int
    dead_letter_rows: int
    watermark_before: datetime | None
    watermark_after: datetime | None
    duckdb_path: Path


def run_pipeline(
    duckdb_path: Path | None = None,
    schema_path: Path | None = None,
    watermark_name: str = DEFAULT_WATERMARK_NAME,
) -> PipelineSummary:
    load_dotenv()

    target_duckdb = duckdb_path or Path(os.getenv("SPRINT7_DUCKDB_PATH", str(DEFAULT_DUCKDB_PATH)))
    target_schema = schema_path or Path(
        os.getenv("SPRINT7_ANALYTICS_SCHEMA_PATH", str(DEFAULT_SCHEMA_PATH))
    )

    batch_id = str(uuid4())
    pg_config = PostgresConfig.from_env()

    logger.info("Trade ETL batch %s started", batch_id)
    with open_duckdb(target_duckdb) as connection:
        initialize_schema(connection, target_schema)
        ensure_etl_tables(connection)

        watermark_before = get_watermark(connection, watermark_name)
        extracted = extract_incremental(pg_config, watermark_before)
        if not extracted:
            logger.info("No new rows found for watermark %s", watermark_before)
            return PipelineSummary(
                batch_id=batch_id,
                extracted_rows=0,
                valid_fact_rows=0,
                dead_letter_rows=0,
                watermark_before=watermark_before,
                watermark_after=watermark_before,
                duckdb_path=target_duckdb,
            )

        transformed = transform_trade_rows(extracted, loaded_at=datetime.now(timezone.utc).replace(tzinfo=None))

        # Load dimensions in required dependency order before facts.
        load_dimensions(connection, transformed.dim_date, transformed.dim_instrument, transformed.dim_account)
        account_keys, instrument_keys, date_keys = get_dimension_key_sets(connection)

        validated = validate_fact_rows(
            transformed.fact_trades,
            existing_account_keys=account_keys,
            existing_instrument_keys=instrument_keys,
            existing_date_keys=date_keys,
            batch_id=batch_id,
        )

        load_facts(connection, validated.valid_facts)
        load_dead_letters(connection, validated.dead_letters)

        # Watermark advances after the batch is fully processed.
        watermark_after = max(
            datetime.fromisoformat(str(row["order_created_at"]))
            if not isinstance(row["order_created_at"], datetime)
            else row["order_created_at"]
            for row in extracted
        )
        update_watermark(connection, watermark_name, watermark_after)

    logger.info(
        "Trade ETL batch %s completed: extracted=%d, valid=%d, dead_letter=%d",
        batch_id,
        len(extracted),
        len(validated.valid_facts),
        len(validated.dead_letters),
    )

    return PipelineSummary(
        batch_id=batch_id,
        extracted_rows=len(extracted),
        valid_fact_rows=len(validated.valid_facts),
        dead_letter_rows=len(validated.dead_letters),
        watermark_before=watermark_before,
        watermark_after=watermark_after,
        duckdb_path=target_duckdb,
    )


def main() -> None:
    load_dotenv()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
        datefmt="%H:%M:%S",
    )

    try:
        summary = run_pipeline()
        print(
            "\nTrade ETL completed successfully\n"
            f"Batch ID: {summary.batch_id}\n"
            f"Extracted rows: {summary.extracted_rows}\n"
            f"Valid facts: {summary.valid_fact_rows}\n"
            f"Dead letters: {summary.dead_letter_rows}\n"
            f"Watermark before: {summary.watermark_before}\n"
            f"Watermark after: {summary.watermark_after}\n"
            f"DuckDB path: {summary.duckdb_path}\n"
        )
    except Exception as exc:
        logger.error("Trade ETL failed: %s", exc, exc_info=True)
        sys.exit(1)


if __name__ == "__main__":
    main()
