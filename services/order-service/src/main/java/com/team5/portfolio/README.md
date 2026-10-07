# Portfolio module

Priced holdings for an account, served by the Trade REST API beside the order and account routes.
Binding contract: [contracts/portfolio-api.yaml](../../../../../../../../contracts/portfolio-api.yaml).
This module covers the portfolio summary, priced positions and `/health`. `/pnl` and the booking of
realised profit and loss belong to the P&L module.

## Routes

| Route | Purpose |
|---|---|
| `GET /api/v1/portfolio/{accountId}` | Cash, market value, cost basis, P&L totals, `partial`, `asOf` |
| `GET /api/v1/portfolio/{accountId}/positions?symbol=` | Open holdings with price, market value, unrealised P&L, `priceAsOf`, `stale` |
| `GET /api/v1/portfolio/{accountId}/candles/{symbol}?range=` | Daily candles for a held instrument. **Not in portfolio-api.yaml**, see below |
| `GET /health` | `ok` / `degraded` with Postgres and Fauxnance status. Always `200` |

Every route compares the token's `accountId` claim with the path first and answers `ACC-403`
("Account not accessible", logged with both ids) before reading anything. Errors: `AUTH-401`,
`ACC-403`, `ACC-404`, `VAL-422`, and `MKT-503` (503) when no held instrument can be priced.

## How a request is priced

1. Quantity, average cost and cash come from `AccountService`, which owns them. Instrument currency
   comes from one read-only query (`PortfolioReadMapper`).
2. `PricingService` asks `QuoteCache` first. Only the misses go to Fauxnance, in **one**
   `GET /quotes?symbols=A,B,C` call (25 symbols maximum per call). The summary adds the `FX:` pairs it
   needs to the same call.
3. Each entry in the response is read on its own. An entry with an `error` is left out; the others are
   priced. Fauxnance answers `200` even when individual symbols fail.
4. If the call fails, the last known quote is served, then the executor's `market_quote_snapshots`
   row, both marked `stale = true`. A symbol with no price anywhere is unpriced.

### Staleness
`stale` is the Fauxnance flag, passed through, plus `true` for any fallback value. It is not a fixed
age: a closed market's last quote can be hours old and still fresh. A stale price is served and
marked, never hidden. `priceAsOf` is the quote's own `asOf`.

### Partial and unavailable
- Some held symbols unpriced: `200`, those positions have null price fields and `stale: true`, and the
  summary has `partial: true`.
- No held symbol priced: `503 MKT-503`. A portfolio with no holdings is a normal `200` and makes no call.
- A currency with no FX quote also sets `partial`; it is never an error.

## Candles (outside the contract)

`GET /api/v1/portfolio/{accountId}/candles/{symbol}?range=1M|3M|6M|1Y` (default `3M`) feeds the
candlestick chart on the Portfolio page. It is **deliberately not in `contracts/portfolio-api.yaml`**,
which is binding and was left unchanged; this section is its only documentation. If the contract owner
later wants it standardised, move this there.

```json
{ "symbol": "INFY.NS", "currency": "INR", "interval": "1d", "range": "3M", "stale": false,
  "asOf": "2026-10-06T15:51:40Z",
  "candles": [ { "date": "2026-10-06", "open": 1018.0000, "high": 1020.5000, "low": 1003.1500,
                 "close": 1013.8500, "volume": 8245427, "synthetic": false } ] }
```

- **Same checks as every route:** token claim against path (`ACC-403`), then the symbol must be one the
  account holds, otherwise `INS-404`. This stops the route being used to spend quota on arbitrary
  symbols. A bad `range` is `VAL-422`.
- **Quota:** Fauxnance's `GET /candles/{symbol}` ignores any date window and returns about a year of
  daily bars per call, one unit each, and cannot be batched. One fetch per symbol is cached for
  `fauxnance.candle-cache-ttl` (default `1h`) and every range is sliced from it, so switching ranges is
  free. A 5-holding account costs at most 5 units an hour.
- **Provider down:** an older cached copy is served with `stale: true`; with no copy at all the answer
  is `503 MKT-503`.
- **Bad bars** (null date or price, non-positive price, `high < low`, negative volume) are dropped
  one by one rather than failing the series.
- **`synthetic`** marks a bar Fauxnance estimated rather than observed. The UI draws it hollow and dashed.

## Definitions
`costBasis = quantity * averageCost`, `marketValue = quantity * lastPrice`,
`unrealisedPnl = marketValue - costBasis`, `totalValue = cash + marketValue`. Unrealised P&L is
never stored. Realised P&L is not computed here: `RealisedPnlProvider` supplies it and the summary
passes it through. `ZeroRealisedPnlProvider` is a stub that applies only until the P&L module
registers its own provider bean.

## Configuration

| Property | Default | Note |
|---|---|---|
| `FAUXNANCE_API_KEY` | none | Put it in `services/order-service/.env`. Without it pricing is unavailable |
| `fauxnance.base-url` | the shared Fauxnance URL | `FAUXNANCE_BASE_URL` overrides it |
| `fauxnance.cache-ttl` | `60s` | Keep below Fauxnance's own freshness window (about 5 minutes) |
| `fauxnance.connect-timeout` / `read-timeout` | `2s` / `4s` | No retries: this is a request path |
| `fauxnance.usage-cache-ttl` | `5m` | The usage call behind `quotaRemaining` costs quota |
| `fauxnance.candle-cache-ttl` | `1h` | One year of daily bars per held symbol; ranges are sliced from it |

Quota: one call per account per cache window at most, and a symbol cached for one account is free for
every other. Two accounts refreshing every second still cost 60 calls an hour, not 7,200.

## Tables
This module has no tables of its own. It reads `instruments` and `market_quote_snapshots` only, and
its mapper contains `SELECT` statements only (`PortfolioReadMapperReadOnlyTest`).

## Decision log

1. **No writes, enforced by a test.** The module shares the connection the order path writes through,
   so nothing in the framework stops an `UPDATE` against the trading tables. Only discipline and
   review do. The mapper is SELECT-only and a test fails the build if an INSERT, UPDATE or DELETE
   appears. Reviewers must also reject any new mapper for this package that writes.
2. **Failures stay inside the module.** Pricing is never called from the order write path. Every
   pricing error is caught and becomes "unpriced", so an outage answers on these routes only.
3. **Batch and cache.** One batched call replaces a per-position loop; quota is 2,000 a day and the
   key is shared across the team.
4. **Fauxnance's `stale` flag, not our own age limit.** Closed markets make a fixed age window wrong.
5. **Summary totals cover priced, convertible holdings only.** Including the cost of an unpriced
   holding would make `unrealisedPnl` hugely negative. `partial` says holdings were left out, and
   `positionCount` still counts them all.
6. **Cost basis converts at today's FX rate.** Both cost and value use the current rate, so
   base-currency unrealised P&L excludes FX gains and losses on the purchase.
7. **In-memory cache.** It is per instance, so it assumes a single instance. With several, each would
   hold its own cache and quota use would multiply; move it to a shared store then.
8. **Unknown symbol and outage look the same.** Fauxnance reports a made-up symbol as
   `UPSTREAM_UNAVAILABLE`, so both are treated as "unpriced right now".
9. **`/health` replaced.** The old controller reported `UP`/`DOWN` and could answer `503`. The contract
   wants `ok`/`degraded`, always `200`, so the service is not restarted over a pricing outage.
   The old controller only checked that Postgres and the auth host resolved in DNS; the new one opens
   a real connection.
10. **Contract unchanged.** Fauxnance also returns `changePercent` (the day's move), but
    `PricedPosition` is closed to extra fields, so it is not exposed.
11. **Snapshot timestamps are read as UTC.** `market_quote_snapshots.updated_at` is a plain
    `TIMESTAMP` written by the executor; if the database session is not UTC the fallback `priceAsOf`
    will be off by the offset.
12. **Candles live outside the contract.** The browser cannot call Fauxnance, so charts need a route
    here. The contract is binding and was kept unchanged, so the route is documented in this README
    only. That is a gap reviewers should know about.
13. **Candles only for held symbols.** Without that check any signed-in user could spend the team's
    shared quota on arbitrary symbols.
14. **Candle cache is per instance and in memory**, like the quote cache, and assumes one instance.
