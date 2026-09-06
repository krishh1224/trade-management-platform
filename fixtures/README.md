# Fixture Files

Three canned Fauxnance API responses for testing without network access.

## `candles_valid.json`

A clean response for **INFY.NS** with 5 well-formed daily candles.
All fields present, all types correct, all prices consistent (high ≥ low,
no negatives).

## `candles_valid_2.json`

A clean response for **AAPL** with 5 well-formed daily candles.
Same guarantees as above but for a US equity.

## `candles_malformed.json`

A deliberately corrupted response for **BROKEN.NS** with 6 candles, each
containing one defect.  The transform must decide what to do with each.

### The six defects

| # | Row date   | Defect                    | Description                                         |
|---|------------|---------------------------|-----------------------------------------------------|
| 1 | 2025-08-01 | **High below low**        | `high` (85.00) is less than `low` (95.00)           |
| 2 | 2025-08-04 | **Negative close price**  | `close` is −5.50                                    |
| 3 | 2025-08-05 | **Negative volume**       | `volume` is −500                                    |
| 4 | (null)     | **Null date**             | `date` is `null` instead of an ISO-8601 string      |
| 5 | 2025-08-07 | **Missing field (`open`)** | The `open` key is entirely absent from the object   |
| 6 | 2025-08-08 | **Non-numeric price**     | `open` is the string `"not_a_number"` instead of a float |

