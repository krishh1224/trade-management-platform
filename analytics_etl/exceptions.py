"""Custom exceptions for the Fauxnance ETL pipeline.

Each exception maps to a distinct failure mode so callers can decide
what to do without inspecting status codes or message strings.
"""


class FauxnanceError(Exception):
    """Base class for all Fauxnance-related errors."""


class QuotaExhaustedError(FauxnanceError):
    """HTTP 429 – daily request quota exhausted.

    Attributes:
        retry_after: Seconds until the quota resets (from the Retry-After header).
    """

    def __init__(self, retry_after: int | None = None):
        self.retry_after = retry_after
        reset_msg = (
            f" Quota resets in {retry_after} seconds."
            if retry_after
            else ""
        )
        super().__init__(
            f"Fauxnance daily quota exhausted.{reset_msg} "
            "Do NOT retry until the quota resets at midnight UTC."
        )


class AuthenticationError(FauxnanceError):
    """HTTP 401 – API key is missing or invalid."""

    def __init__(self):
        super().__init__(
            "Fauxnance API key is missing or invalid. "
            "Set FAUXNANCE_API_KEY in your .env file."
        )


class SymbolNotFoundError(FauxnanceError):
    """HTTP 404 – the requested symbol does not exist on Fauxnance."""

    def __init__(self, symbol: str):
        self.symbol = symbol
        super().__init__(
            f"Symbol '{symbol}' is not served by Fauxnance."
        )


class BadRequestError(FauxnanceError):
    """HTTP 400 – the request parameters are invalid."""

    def __init__(self, detail: str = ""):
        msg = "Bad request to Fauxnance API."
        if detail:
            msg += f" Detail: {detail}"
        super().__init__(msg)


class ExtractionError(FauxnanceError):
    """Network-level failure after retries are exhausted."""

    def __init__(self, symbol: str, cause: Exception):
        self.symbol = symbol
        self.cause = cause
        super().__init__(
            f"Failed to extract data for '{symbol}' after retries: {cause}"
        )

