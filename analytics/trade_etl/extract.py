from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Callable

try:
    import psycopg
    from psycopg.rows import dict_row
except ImportError:  # pragma: no cover - covered by dependency/runtime checks
    psycopg = None
    dict_row = None

logger = logging.getLogger(__name__)

EXTRACT_SQL = """
SELECT
    o.order_id,
    o.created_at AS order_created_at,
    o.quantity,
    o.price,
    o.status,
    o.side,
    o.instrument_id,
    i.ticker AS instrument_symbol,
    i.name AS instrument_name,
    i.asset_class,
    i.currency AS instrument_currency,
    i.exchange,
    i.tradable,
    p.account_id,
    a.account_number,
    a.status AS account_status,
    a.created_at AS account_created_at,
    c.name AS holder_name
FROM orders o
INNER JOIN portfolio p ON p.portfolio_id = o.portfolio_id
INNER JOIN accounts a ON a.acc_id = p.account_id
INNER JOIN clients c ON c.id = a.client_id
INNER JOIN instruments i ON i.instrument_id = o.instrument_id
WHERE (%(watermark)s::timestamp IS NULL OR o.created_at > %(watermark)s::timestamp)
ORDER BY o.created_at ASC, o.order_id ASC
"""


@dataclass(frozen=True)
class PostgresConfig:
    host: str
    port: int
    database: str
    user: str
    password: str

    @classmethod
    def from_env(cls) -> "PostgresConfig":
        required = {
            "POSTGRES_HOST": os.getenv("POSTGRES_HOST"),
            "POSTGRES_PORT": os.getenv("POSTGRES_PORT"),
            "POSTGRES_DB": os.getenv("POSTGRES_DB"),
            "POSTGRES_USER": os.getenv("POSTGRES_USER"),
            "POSTGRES_PASSWORD": os.getenv("POSTGRES_PASSWORD"),
        }

        missing = [name for name, value in required.items() if not value]
        if missing:
            raise ValueError(
                "Missing PostgreSQL environment variables: " + ", ".join(sorted(missing))
            )

        return cls(
            host=required["POSTGRES_HOST"] or "",
            port=int(required["POSTGRES_PORT"] or "5432"),
            database=required["POSTGRES_DB"] or "",
            user=required["POSTGRES_USER"] or "",
            password=required["POSTGRES_PASSWORD"] or "",
        )


ConnectionFactory = Callable[[PostgresConfig], Any]


def _default_connection_factory(config: PostgresConfig):
    if psycopg is None or dict_row is None:
        raise RuntimeError(
            "psycopg is required for PostgreSQL extraction. Install psycopg[binary]."
        )

    return psycopg.connect(
        host=config.host,
        port=config.port,
        dbname=config.database,
        user=config.user,
        password=config.password,
        row_factory=dict_row,
    )


def extract_incremental(
    config: PostgresConfig,
    watermark: datetime | None,
    connection_factory: ConnectionFactory | None = None,
) -> list[dict[str, Any]]:
    factory = connection_factory or _default_connection_factory
    logger.info("Extracting orders with watermark: %s", watermark)

    with factory(config) as conn:
        with conn.cursor() as cursor:
            cursor.execute(EXTRACT_SQL, {"watermark": watermark})
            rows = cursor.fetchall()

    records = [dict(row) for row in rows]
    logger.info("Extracted %d order rows", len(records))
    return records
