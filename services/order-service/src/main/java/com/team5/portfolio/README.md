# Portfolio module

Priced holdings for an account, served by the Trade REST API beside the order and account routes.
Binding contract: [contracts/portfolio-api.yaml](../../../../../../../../contracts/portfolio-api.yaml).
This module covers the portfolio summary, priced positions, profit and loss (realised and unrealised)
and `/health`.

## Routes

| Route | Purpose |
|---|---|
| `GET /api/v1/portfolio/{accountId}` | Cash, market value, cost basis, P&L totals, `partial`, `asOf` |
| `GET /api/v1/portfolio/{accountId}/positions?symbol=` | Open holdings with price, market value, unrealised P&L, `priceAsOf`, `stale` |
| `GET /api/v1/portfolio/{accountId}/pnl?from=&to=&bySymbol=` | Realised, unrealised and total P&L, optionally per instrument and bounded by date |
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
`unrealisedPnl = marketValue - costBasis`, `totalValue = cash + marketValue`,
`totalPnl = realisedPnl + unrealisedPnl`. Unrealised P&L is never stored. Realised P&L is
`(fillPrice - averageCostAtSale) * quantitySold`, booked once at the sale and stored in the ledger.

## Profit and loss

`GET /pnl` bounds only the realised figure: `from` and `to` are inclusive dates (`yyyy-MM-dd`), either
may be omitted, and `from` later than `to` is `VAL-422`. Unrealised is always as at now and comes from the
same valuation the summary uses, so the two routes agree to the cent. `bySymbol=true` adds a row for
every instrument that is held or has realised profit in the period (a position sold out still shows),
ordered by symbol; without it the key is absent. As on the summary, a holding that cannot be priced or
converted contributes nothing to unrealised, and when nothing held can be priced the answer is `503 MKT-503`.

### Realised P&L: how a sale is booked
1. **The ledger.** `realised_pnl_ledger` (migration `V15_realised_pnl_ledger.sql`) holds one row per
   sold order, keyed by `order_id`. A row is inserted the first time the sale is seen and never updated,
   so the figure does not move with today's price.
2. **Idempotent.** The insert is `ON CONFLICT (order_id) DO NOTHING`. Reading the ledger a second time, a
   replayed event, or two requests racing book nothing extra and count nothing twice.
3. **When.** Whenever the ledger is read (the summary or `/pnl`), filled sales not yet in it are booked
   first, so a sale shows up on the next request.
4. **Average cost at sale.** Nothing records it: the executor's `ORDER_FILLED` event carries no cost, a sell
   leaves the holding's average unchanged, and a full sale deletes the holding. `CostBasisReplay` rebuilds
   it from the filled orders, applying the executor's own arithmetic (a buy re-weights the average, rounded
   half-up to 4 places; a sell does not change it). The replay is anchored on the holding as it stands now
   by undoing the fills backwards, which also recovers an opening position loaded straight into the
   holdings without an order. With no current holding it starts from nothing.
5. **Currency.** `realisedPnl` is in the instrument's currency; the base-currency figure uses the FX rate
   the order filled at (`orders.fx_rate`, 1 when the instrument is in the base currency).
6. **When a sale cannot be booked.** A sale the history cannot explain (it sells more than the replay
   holds), or a foreign-currency sale with no recorded FX rate, is logged and left out. It is never
   guessed.

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

- **Owned and written:** `realised_pnl_ledger`, the only table this module writes. Its mapper
  (`PnlLedgerMapper.xml`) names no other table in a write, enforced by `PnlLedgerMapperIntegrationTest`.
  Apply `databases/postgresql/migrations/V15_realised_pnl_ledger.sql` (the existing `apply.sh` picks it up).
- **Read only:** `instruments`, `market_quote_snapshots` and `orders`, through `PortfolioReadMapper.xml`,
  which contains `SELECT` statements only (`PortfolioReadMapperReadOnlyTest`). Quantity, average cost and
  cash come through `AccountService`.

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
15. **Sales are booked by replaying orders, not from Kafka.** The executor's `ORDER_FILLED` event has no
    average cost and a full sale deletes the holding, so neither can say what a sale cost. Replaying the
    filled orders can, and it needs no broker, so the ledger fills on first read and works for sales that
    predate the module.
16. **The current holding outranks the order history.** Where the two disagree, as in the seed data (an
    account holds 80 units but its orders only explain 10), the cost comes from the holding, because the
    holdings table is what the executor actually maintains. Costs recovered this way carry the rounding of
    the executor's 4-place averages.
17. **Realised P&L converts at the sale's FX rate, unrealised at today's.** So realised profit includes
    the currency move to the sale date and unrealised does not include the move since purchase (see 6).
18. **A second write path, kept to one table.** The ledger needs inserts, so the module is no longer
    read-only. The rule is now "writes only its own table", held by a test on the ledger mapper.
