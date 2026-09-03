class FauxnanceError(Exception):
    """Base class for all Fauxnance-related errors."""


class QuotaExhaustedError(FauxnanceError):
    #HTTP 429-daily request quota exhausted so it waits until retry_after seconds
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
    #HTTP 401-API key is missing 
    def __init__(self):
        super().__init__(
            "Fauxnance API key is missing or invalid. "
            "Set FAUXNANCE_API_KEY in your .env file."
        )

class SymbolNotFoundError(FauxnanceError):
    #HTTP 404-symbol does not exist 
    def __init__(self, symbol: str):
        self.symbol = symbol
        super().__init__(
            f"Symbol '{symbol}' is not served by Fauxnance."
        )
class BadRequestError(FauxnanceError):
    #HTTP 400–the request parameters are invalid
    def __init__(self, detail: str = ""):
        msg = "Bad request to Fauxnance API."
        if detail:
            msg += f" Detail: {detail}"
        super().__init__(msg)

class ExtractionError(FauxnanceError):
    # catches network level failure after all retries are exhausted
    def __init__(self, symbol: str, cause: Exception):
        self.symbol = symbol
        self.cause = cause
        super().__init__(
            f"Failed to extract data for '{symbol}' after retries: {cause}"
        )
        
class ServerError(Exception):
    """
    Raised when the Fauxnance API returns
    a 5xx server-side failure.
    """

    def __init__(
        self,
        status_code: int,
        message: str = "",
    ) -> None:

        self.status_code = status_code
        self.message = message

        super().__init__(
            f"Server error {status_code}: {message}"
        )

