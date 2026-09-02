from __future__ import annotations
import hashlib
import json
import logging
import os
import time
from pathlib import Path
from typing import Any
import requests
from dotenv import load_dotenv

from analytics_etl.exceptions import (
    AuthenticationError,
    BadRequestError,
    ExtractionError,
    QuotaExhaustedError,
    SymbolNotFoundError,
)

logger = logging.getLogger(__name__)

BASE_URL = "https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1"
CACHE_DIR = Path(".cache")

# Retry settings for network errors
MAX_RETRIES = 3
INITIAL_BACKOFF_SECONDS = 1.0
REQUEST_TIMEOUT_SECONDS = 30
class FauxnanceClient:
    
    def __init__(self, cache_dir: Path = CACHE_DIR) -> None:
        load_dotenv()  # loads .env into os.environ if present
        self._api_key: str | None = os.environ.get("FAUXNANCE_API_KEY")
        self._cache_dir = Path(cache_dir)
        self._cache_dir.mkdir(parents=True, exist_ok=True)
        self._session = requests.Session()

    def health(self) -> dict[str, Any]:
        """``GET /health`` – no key required."""
        return self._request("GET", "/health", auth_required=False)

    def usage(self) -> dict[str, Any]:
        """``GET /usage`` – returns quota consumption for the current key."""
        return self._request("GET", "/usage")

    def candles(
        self,
        symbol: str,
        start: str | None = None,
        end: str | None = None,
    ) -> dict[str, Any]:
        """``GET /candles/{symbol}`` – returns daily OHLCV candles."""

        # Check cache first
        cached = self._cache_read(symbol, start, end)
        if cached is not None:
            logger.info("Cache hit for %s [%s → %s]", symbol, start, end)
            return cached
        params: dict[str, str] = {}
        if start:
            params["start"] = start
        if end:
            params["end"] = end

        data = self._request("GET", f"/candles/{symbol}", params=params)

        # Write raw response to cache
        self._cache_write(symbol, start, end, data)
        logger.info("Fetched and cached %s [%s → %s]", symbol, start, end)
        return data

    def _request(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, str] | None = None,
        auth_required: bool = True,
    ) -> dict[str, Any]:
        """Send an HTTP request with retry / back-off and status dispatch.

        Raises
        ------
        AuthenticationError
            On 401.
        SymbolNotFoundError
            On 404 (the symbol component is parsed from *path*).
        BadRequestError
            On 400.
        QuotaExhaustedError
            On 429.
        ExtractionError
            When all retries are exhausted on a transient failure.
        """
        url = f"{BASE_URL}{path}"
        headers: dict[str, str] = {}
        if auth_required:
            if not self._api_key:
                raise AuthenticationError()
            headers["X-Api-Key"] = self._api_key

        last_exc: Exception | None = None

        for attempt in range(1, MAX_RETRIES + 1):
            try:
                resp = self._session.request(
                    method,
                    url,
                    headers=headers,
                    params=params,
                    timeout=REQUEST_TIMEOUT_SECONDS,
                )
                return self._handle_status(resp, path)

            except (requests.ConnectionError, requests.Timeout) as exc:
                last_exc = exc
                if attempt < MAX_RETRIES:
                    wait = INITIAL_BACKOFF_SECONDS * (2 ** (attempt - 1))
                    logger.warning(
                        "Transient error on %s (attempt %d/%d), "
                        "retrying in %.1fs: %s",
                        path, attempt, MAX_RETRIES, wait, exc,
                    )
                    time.sleep(wait)
                else:
                    logger.error(
                        "All %d retries exhausted for %s: %s",
                        MAX_RETRIES, path, exc,
                    )

        symbol = path.rsplit("/", 1)[-1] if "/" in path else path
        raise ExtractionError(symbol, last_exc)  # type: ignore[arg-type]

    @staticmethod
    def _handle_status(resp: requests.Response, path: str) -> dict[str, Any]:
        """Map HTTP status codes to domain exceptions."""
        if resp.ok:
            return resp.json()

        status = resp.status_code

        if status == 401:
            logger.error("Authentication failed (401) for %s", path)
            raise AuthenticationError()
        
        if status == 403:
            logger.error("Access forbidden (403) for %s", path)
            resp.raise_for_status()

        if status == 429:
            retry_after = resp.headers.get("Retry-After")
            retry_seconds = int(retry_after) if retry_after else None
            logger.error(
                "Quota exhausted (429) for %s. Retry-After: %s seconds",
                path, retry_seconds,
            )
            raise QuotaExhaustedError(retry_after=retry_seconds)

        if status == 404:
            symbol = path.rsplit("/", 1)[-1] if "/" in path else path
            logger.warning("Symbol not found (404): %s", symbol)
            raise SymbolNotFoundError(symbol)

        if status == 400:
            detail = ""
            try:
                detail = resp.json().get("message", resp.text)
            except ValueError:
                detail = resp.text
            logger.error("Bad request (400) for %s: %s", path, detail)
            raise BadRequestError(detail)

        resp.raise_for_status()
        return {}  

    # cache layer 

    def _cache_key(
        self,
        symbol: str,
        start: str | None,
        end: str | None,
    ) -> Path:
        """Build a filesystem-safe cache path for this request."""
        safe_symbol = symbol.replace(":", "_").replace("/", "_")
        tag = f"{safe_symbol}_{start or 'none'}_{end or 'none'}"
        # using a short hash suffix here to avoid collisions on very long tags
        h = hashlib.md5(tag.encode()).hexdigest()[:8]
        return self._cache_dir / f"{safe_symbol}_{start or 'none'}_{end or 'none'}_{h}.json"

    def _cache_read(
        self,
        symbol: str,
        start: str | None,
        end: str | None,
    ) -> dict[str, Any] | None:
        """Return cached raw response, or ``None`` on miss."""
        path = self._cache_key(symbol, start, end)
        if path.exists():
            try:
                return json.loads(path.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError) as exc:
                logger.warning("Corrupt cache file %s, ignoring: %s", path, exc)
                return None
        return None

    def _cache_write(
        self,
        symbol: str,
        start: str | None,
        end: str | None,
        data: dict[str, Any],
    ) -> None:
        """Persist the raw API response to disk."""
        path = self._cache_key(symbol, start, end)
        try:
            path.write_text(
                json.dumps(data, indent=2, ensure_ascii=False),
                encoding="utf-8",
            )
        except OSError as exc:
            logger.warning("Could not write cache file %s: %s", path, exc)


# Extract function

def extract(
    symbols: list[str],
    start: str | None = None,
    end: str | None = None,
    cache_dir: Path = CACHE_DIR,
) -> dict[str, dict[str, Any]]:
    
    client = FauxnanceClient(cache_dir=cache_dir)

    # check that the API is reachable
    try:
        health = client.health()
        logger.info("Fauxnance API is healthy: %s", health.get("data", {}).get("status"))
    except requests.ConnectionError:
        logger.error("Cannot reach Fauxnance API – is the network up?")
        raise

    results: dict[str, dict[str, Any]] = {}
    skipped: list[str] = []

    for symbol in symbols:
        try:
            raw = client.candles(symbol, start=start, end=end)
            results[symbol] = raw
        except (SymbolNotFoundError, BadRequestError) as exc:
            # skip and continue
            logger.warning("Skipping %s: %s", symbol, exc)
            skipped.append(symbol)
        except (QuotaExhaustedError, AuthenticationError):
            # Fatal
            logger.error(
                "Fatal API error – stopping extraction. "
                "Extracted %d/%d symbols before failure.",
                len(results), len(symbols),
            )
            raise

    if skipped:
        logger.warning(
            "Extraction complete. Skipped %d symbol(s): %s",
            len(skipped), ", ".join(skipped),
        )
    logger.info(
        "Extracted %d/%d symbols successfully.", len(results), len(symbols),
    )
    return results

