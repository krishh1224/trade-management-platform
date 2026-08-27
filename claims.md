# Claims

## Symbol universe

| Symbol | Company | Why it is in scope |
|---|---|---|
| INFY.NS | Infosys Limited (NSE) | |
| RELIANCE.NS | Reliance Industries (NSE) | |
| AAPL | Apple Inc. (US) | |
| TATASTEEL.BO | Tata Steel Limited (BSE) | |

Tata Steel has a shorter history than the other three symbols (data starts
17 July 2026), so it is not included in the
return/volatility/trend comparison, which requires a shared period across
all symbols being compared. It is instead given its own claim (4 below),
based on what its own available window shows.

Data source: `stock_prices` table in the DuckDB analytical store, loaded by
the pipeline's extract-transform-load stages.

## Period covered (Claims 1-3)

2025-08-28 to 2026-08-26

## Claim 1

Between 28 August 2025 and 26 August 2026,
Apple Inc. (US) delivered the strongest cumulative return of the three
symbols sampled, at +34.8%, while Infosys Limited (NSE)
was the weakest performer at -25.3%.

Supporting chart: `charts/claim1_returns.html`

## Claim 2

Over the same 28 August 2025 to 26 August 2026
window, Infosys Limited (NSE) was the most volatile of the three symbols,
with a daily-return standard deviation of 1.88%,
compared with just 1.27% for
Reliance Industries (NSE), the calmest symbol in the sample.

Supporting chart: `charts/claim2_volatility.html`

## Claim 3

Indexed to a common starting value of 100 on 28 August 2025,
Apple Inc. (US) trended consistently upward to finish at
134.8 by 26 August 2026, while
Infosys Limited (NSE) finished at 74.7, showing
the two symbols diverged sharply over the period.

Supporting chart: `charts/claim3_trend.html`

## Claim 4

Tata Steel Limited (BSE: TATASTEEL.BO) traded 6,350,100 shares on
20 August 2026, about 5.8 times the stock's
average daily volume of 1,091,780 shares on other days between
17 July 2026 and 26 August 2026, the
period covered by its available data.

Supporting chart: `charts/claim4_tatasteel_volume_spike.html`
