# Secret-free production bundle (Team 5)

## How to run the check
From `frontend/trade-ui`:

```
npm run check:bundle
```

This runs [scripts/check-bundle-secrets.js](scripts/check-bundle-secrets.js). It builds production, then scans every file in
`dist/trade-ui`, and exits 0 with `OK: bundle is secret-free` or exits 1 listing each problem.

To scan the existing `dist` without rebuilding:

```
SKIP_BUILD=1 npm run check:bundle          # Git Bash
$env:SKIP_BUILD=1; npm run check:bundle    # PowerShell
```

## What the script checks
1. **Source maps:** any `.map` file fails the check (a source map is the source code back again).
2. **Host and key patterns:** `execute-api`, `amazonaws`, `fauxnance`, `x-api-key`, `api-key`, `jwt_secret`,
   `shared-secret`, `client_secret`, `private-key`, `sourceMappingURL`.
3. **Literal values:** the value of every variable in the real `.env` files in the repo is searched for in the bundle,
   so a secret copied under a different name is still caught.

4. **Criteria 2 (UI never calls Fauxnance):** every UI source file (specs excluded) is scanned. It fails on the market-data
   host or key, and on any absolute URL whose host is not localhost (our trade-api / auth), Google Fonts or github.com.
5. **Task 3 (revoke):** the script cannot revoke a key for you. On a leak it prints, per leaked variable, what to revoke and
   redeploy, and states that deleting the line is not enough.

Notes:
- If no real `.env` values are found (for example a fresh machine) the check fails, because the literal-value check
  could not run. Set `ALLOW_NO_ENV=1` to skip that on purpose.
- `.env.example` files are ignored (dummy values). Only files named exactly `.env` are read.
- Values shorter than 8 characters, and generic ones (`localhost`, `127.0.0.1`, `true`, `false`), are skipped.
- Secret values are never printed. A failure names only the variable and the file.
- `sourceMap` is explicitly `false` in the production configuration (`angular.json`).

## Results (2026-10-05)
| Check | Result |
|---|---|
| Source maps in production output | none |
| Host / key / secret patterns | 0 hits |
| Literal values from real `.env` files (46 values) | 0 hits |
| UI source: market-data host/key, or any other external host (82 files) | none |
| Negative tests: fake `x-api-key`; real `.env` value in the bundle; Fauxnance URL in UI source; unknown host in UI source; no `.env` present | each failed with exit 1 as expected |

## Criteria 2: prices reach the browser through our service
Enforced by check 4 above, and confirmed in the existing code:
Executor `MarketDataPoller` (holds `FAUXNANCE_API_KEY`, calls Fauxnance on a schedule) writes `market_quote_snapshots`
-> trade-api `WatchlistMapper.xml` joins that table as `lastPrice` -> `GET /api/v1/watchlists` -> `TradeService.getWatchlist`
-> watchlist page shows `lastPrice`. The only `HttpClient` users in the UI are `auth.service`, `trade.service` and
`provide-api`, all pointed at trade-api / auth. The UI never contacts Fauxnance.

## Task 3
No key or secret reached the bundle, so nothing needs revoking. If the check ever fails on a real key, treat the key as
published: the script prints the revocation steps (new Fauxnance key via the instructor; new `JWT_SECRET` with
`openssl rand -base64 32` and redeploy of nest-auth, trade-api and executor). Deleting the line is not enough.
