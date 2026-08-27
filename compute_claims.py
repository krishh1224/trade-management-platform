"""
Story 7: four business claims, sourced from the DuckDB store instead of raw JSON.

Claims 1-3 compare three symbols that share a full year of history:
  1. Cumulative return over the period       -> bar chart
  2. Volatility (std dev of daily returns)   -> bar chart, one color per stock
  3. Normalized price trend, all 3 together  -> line chart

Tata Steel has a shorter history and is given its own separate claim instead
of being forced into an unfair comparison:
  4. Tata Steel  - volume spike, own window   -> bar chart

Reads all data from the stock_prices table in analytics.duckdb (populated
by the pipeline's load stage) and produces:
  - charts/claim1_returns.html
  - charts/claim2_volatility.html
  - charts/claim3_trend.html
  - charts/claim4_tatasteel_volume_spike.html
  - claims.md
"""
from pathlib import Path

import duckdb
import pandas as pd
import plotly.graph_objects as go

# --- Point this at your actual database file ---
# The pipeline's load() function creates this file wherever db_path is passed
# in as a parameter when the ETL runs - by default, "analytics.duckdb" in the
# folder the pipeline was run from. Copy that file next to this script, or
# change the path below to point at it wherever it actually sits.

DB_PATH = "analytics.duckdb"


# --- The three symbols with a full year of shared history ---
SYMBOL_KEYS = ["INFY.NS", "RELIANCE.NS", "AAPL"]

# --- The symbol with shorter history, claimed separately ---
TATASTEEL_SYMBOL = "TATASTEEL.BO"

DISPLAY_NAMES = {
    "INFY.NS": "Infosys Limited (NSE)",
    "RELIANCE.NS": "Reliance Industries (NSE)",
    "AAPL": "Apple Inc. (US)",
    "TATASTEEL.BO": "Tata Steel Limited (BSE)",
}

# Fixed color per company, used only in Claim 2 (see the earlier turns for
# why only that chart is colored this way). Tata Steel (Claim 4) uses its
# own green/orange scheme, unrelated to this palette.
COLOR_MAP = {
    "AAPL": "green",
    "INFY.NS": "blue",
    "RELIANCE.NS": "red",
}

CHARTS_DIR = Path("charts")
CHARTS_DIR.mkdir(exist_ok=True)


def load_symbol_from_db(con, symbol):
    """Query one symbol's rows out of stock_prices.

    Applies the same quality gate as before: real trades only (not
    synthetic), no nulls in the price fields, no high-below-low rows.
    """
    df = con.execute(
        """
        SELECT symbol, date, open, high, low, close, volume, synthetic
        FROM stock_prices
        WHERE symbol = ?
        ORDER BY date
        """,
        [symbol],
    ).fetchdf()

    if df.empty:
        raise ValueError(
            f"No rows found in stock_prices for symbol '{symbol}'. "
            f"Check the pipeline has actually loaded this symbol, and "
            f"that the spelling matches exactly what's stored in the table."
        )

    df["date"] = pd.to_datetime(df["date"])
    df = df[df["synthetic"] == False].copy()          # real trades only
    df = df.dropna(subset=["close", "high", "low"])
    df = df[df["high"] >= df["low"]]
    df = df.sort_values("date").reset_index(drop=True)
    return df


def display_name(symbol):
    return DISPLAY_NAMES.get(symbol, symbol)


def cumulative_return_pct(df):
    first = df["close"].iloc[0]
    last = df["close"].iloc[-1]
    return (last - first) / first * 100


def volatility_pct(df):
    daily_returns = df["close"].pct_change().dropna()
    return daily_returns.std() * 100


def volume_spike(df):
    """The single biggest volume day versus the average of every other day."""
    vol = df.dropna(subset=["volume"])
    spike_idx = vol["volume"].idxmax()
    spike_date = vol.loc[spike_idx, "date"]
    spike_vol = vol.loc[spike_idx, "volume"]
    avg_other = vol.drop(index=spike_idx)["volume"].mean()
    ratio = spike_vol / avg_other
    return spike_date, spike_vol, avg_other, ratio


if __name__ == "__main__":
    con = duckdb.connect(DB_PATH, read_only=True)

    loaded = {}
    for key in SYMBOL_KEYS:
        df = load_symbol_from_db(con, key)
        loaded[key] = {"name": display_name(key), "df": df}

    # Show each symbol's own range before trimming, so a mismatch is visible
    period_starts = {k: v["df"]["date"].iloc[0] for k, v in loaded.items()}
    period_ends = {k: v["df"]["date"].iloc[-1] for k, v in loaded.items()}

    print("Date range per symbol (Claims 1-3), as stored in DuckDB:")
    for k, v in loaded.items():
        print(f"  {v['name']:35s} {period_starts[k].date()} to {period_ends[k].date()}")
    print()

    start_spread = (max(period_starts.values()) - min(period_starts.values())).days
    end_spread = (max(period_ends.values()) - min(period_ends.values())).days
    if start_spread > 5 or end_spread > 5:
        print(f"NOTE: these three still do not fully align "
              f"(start spread {start_spread}d, end spread {end_spread}d). "
              f"Trimming to the overlap.\n")

    common_start = max(period_starts.values())
    common_end = min(period_ends.values())

    for k, info in loaded.items():
        df = info["df"]
        info["df"] = df[(df["date"] >= common_start) & (df["date"] <= common_end)].reset_index(drop=True)

    period_start = common_start
    period_end = common_end
    print(f"Common period used for Claims 1-3: {period_start.date()} to {period_end.date()}\n")

    for key, info in loaded.items():
        info["return_pct"] = cumulative_return_pct(info["df"])
        info["volatility_pct"] = volatility_pct(info["df"])
        info["df"]["normalized"] = info["df"]["close"] / info["df"]["close"].iloc[0] * 100

    by_return = sorted(loaded.items(), key=lambda kv: kv[1]["return_pct"], reverse=True)
    by_volatility = sorted(loaded.items(), key=lambda kv: kv[1]["volatility_pct"], reverse=True)

    best_key, best_info = by_return[0]
    worst_key, worst_info = by_return[-1]
    riskiest_key, riskiest_info = by_volatility[0]
    calmest_key, calmest_info = by_volatility[-1]

    print("Claim 1 - Cumulative return, best to worst")
    for k, v in by_return:
        print(f"  {v['name']:35s} {v['return_pct']:+.1f}%")

    print("\nClaim 2 - Volatility, riskiest to calmest")
    for k, v in by_volatility:
        print(f"  {v['name']:35s} {v['volatility_pct']:.2f}% daily std dev")

    print("\nClaim 3 - Trend")
    print(f"  Strongest uptrend: {best_info['name']} ({best_info['return_pct']:+.1f}%)")
    print(f"  Weakest performer: {worst_info['name']} ({worst_info['return_pct']:+.1f}%)")

    # --- Chart 1: cumulative return bar chart ---
    names1 = [v["name"] for k, v in by_return]
    returns1 = [v["return_pct"] for k, v in by_return]
    colors1 = ["#1D9E75" if r >= 0 else "#D85A30" for r in returns1]
    fig1 = go.Figure(go.Bar(
        x=names1, y=returns1, marker_color=colors1,
        text=[f"{r:+.1f}%" for r in returns1], textposition="outside",
    ))
    fig1.update_layout(
        title=(f"{best_info['name']} delivered the strongest return "
               f"({best_info['return_pct']:+.1f}%), while {worst_info['name']} "
               f"performed weakest ({worst_info['return_pct']:+.1f}%), "
               f"{period_start.strftime('%b %Y')} to {period_end.strftime('%b %Y')}"),
        xaxis_title="Symbol",
        yaxis_title="Cumulative return (percent)",
        template="plotly_white",
    )
    fig1.write_html(CHARTS_DIR / "claim1_returns.html", include_plotlyjs="inline")

    # --- Chart 2: volatility bar chart, one color per stock ---
    names2 = [v["name"] for k, v in by_volatility]
    vols2 = [v["volatility_pct"] for k, v in by_volatility]
    colors2 = [COLOR_MAP[k] for k, v in by_volatility]
    fig2 = go.Figure(go.Bar(
        x=names2, y=vols2, marker_color=colors2,
        text=[f"{v:.2f}%" for v in vols2], textposition="outside",
    ))
    fig2.update_layout(
        title=(f"{riskiest_info['name']} showed the highest day-to-day volatility "
               f"({riskiest_info['volatility_pct']:.2f}%), making it the riskiest "
               f"of the three symbols, {period_start.strftime('%b %Y')} to "
               f"{period_end.strftime('%b %Y')}"),
        xaxis_title="Symbol",
        yaxis_title="Volatility (standard deviation of daily returns, percent)",
        template="plotly_white",
    )
    fig2.write_html(CHARTS_DIR / "claim2_volatility.html", include_plotlyjs="inline")

    # --- Chart 3: normalized trend line chart, all 3 together ---
    fig3 = go.Figure()
    for k, v in loaded.items():
        fig3.add_trace(go.Scatter(
            x=v["df"]["date"], y=v["df"]["normalized"],
            mode="lines", name=v["name"],
        ))
    fig3.add_hline(y=100, line_dash="dot", line_color="gray")
    fig3.update_layout(
        title=(f"{best_info['name']} trended up {best_info['return_pct']:+.1f}% "
               f"while {worst_info['name']} moved {worst_info['return_pct']:+.1f}%, "
               f"indexed to 100 at {period_start.strftime('%b %Y')}, "
               f"through {period_end.strftime('%b %Y')}"),
        xaxis_title="Trading day",
        yaxis_title="Price indexed to 100 at the start of the period",
        legend_title="Symbol",
        template="plotly_white",
    )
    fig3.write_html(CHARTS_DIR / "claim3_trend.html", include_plotlyjs="inline")

    print(f"\nCharts written to {CHARTS_DIR.resolve()}/")

    # --- Claim 4: Tata Steel on its own terms (shorter history, own window) ---
    tata_df = load_symbol_from_db(con, TATASTEEL_SYMBOL)
    tata_name = display_name(TATASTEEL_SYMBOL)
    tata_start = tata_df["date"].iloc[0]
    tata_end = tata_df["date"].iloc[-1]
    tata_spike_d, tata_spike_v, tata_avg_v, tata_ratio = volume_spike(tata_df)

    print(f"\nClaim 4 - Tata Steel volume spike (own window: "
          f"{tata_start.date()} to {tata_end.date()})")
    print(f"  spike day {tata_spike_d.date()}  {tata_spike_v:,.0f} shares")
    print(f"  average other days  {tata_avg_v:,.0f} shares")
    print(f"  ratio {tata_ratio:.1f}x")

    tata_vol_df = tata_df.dropna(subset=["volume"])
    colors4 = ["#D85A30" if d == tata_spike_d else "#1D9E75" for d in tata_vol_df["date"]]
    fig4 = go.Figure(go.Bar(x=tata_vol_df["date"], y=tata_vol_df["volume"], marker_color=colors4))
    fig4.update_layout(
        title=(f"Tata Steel Limited (BSE: TATASTEEL.BO) volume spiked to "
               f"{tata_spike_v:,.0f} shares on {tata_spike_d.strftime('%d %b %Y')}, "
               f"{tata_ratio:.1f}x the average of other days, "
               f"{tata_start.strftime('%d %b')} to {tata_end.strftime('%d %b %Y')}"),
        xaxis_title="Trading day",
        yaxis_title="Shares traded",
        template="plotly_white",
    )
    fig4.write_html(CHARTS_DIR / "claim4_tatasteel_volume_spike.html", include_plotlyjs="inline")
    print(f"Chart written: {CHARTS_DIR / 'claim4_tatasteel_volume_spike.html'}")

    con.close()

    # --- claims.md ---
    symbol_rows = "\n".join(f"| {k} | {v['name']} | |" for k, v in loaded.items())
    symbol_rows += f"\n| {TATASTEEL_SYMBOL} | {tata_name} | |"

    claims_md = f"""# Claims

## Symbol universe

| Symbol | Company | Why it is in scope |
|---|---|---|
{symbol_rows}

Tata Steel has a shorter history than the other three symbols (data starts
{tata_start.strftime('%d %B %Y')}), so it is not included in the
return/volatility/trend comparison, which requires a shared period across
all symbols being compared. It is instead given its own claim (4 below),
based on what its own available window shows.

Data source: `stock_prices` table in the DuckDB analytical store, loaded by
the pipeline's extract-transform-load stages.

## Period covered (Claims 1-3)

{period_start.date()} to {period_end.date()}

## Claim 1

Between {period_start.strftime('%d %B %Y')} and {period_end.strftime('%d %B %Y')},
{best_info['name']} delivered the strongest cumulative return of the three
symbols sampled, at {best_info['return_pct']:+.1f}%, while {worst_info['name']}
was the weakest performer at {worst_info['return_pct']:+.1f}%.

Supporting chart: `charts/claim1_returns.html`

## Claim 2

Over the same {period_start.strftime('%d %B %Y')} to {period_end.strftime('%d %B %Y')}
window, {riskiest_info['name']} was the most volatile of the three symbols,
with a daily-return standard deviation of {riskiest_info['volatility_pct']:.2f}%,
compared with just {calmest_info['volatility_pct']:.2f}% for
{calmest_info['name']}, the calmest symbol in the sample.

Supporting chart: `charts/claim2_volatility.html`

## Claim 3

Indexed to a common starting value of 100 on {period_start.strftime('%d %B %Y')},
{best_info['name']} trended consistently upward to finish at
{100 + best_info['return_pct']:.1f} by {period_end.strftime('%d %B %Y')}, while
{worst_info['name']} finished at {100 + worst_info['return_pct']:.1f}, showing
the two symbols diverged sharply over the period.

Supporting chart: `charts/claim3_trend.html`

## Claim 4

Tata Steel Limited (BSE: TATASTEEL.BO) traded {tata_spike_v:,.0f} shares on
{tata_spike_d.strftime('%d %B %Y')}, about {tata_ratio:.1f} times the stock's
average daily volume of {tata_avg_v:,.0f} shares on other days between
{tata_start.strftime('%d %B %Y')} and {tata_end.strftime('%d %B %Y')}, the
period covered by its available data.

Supporting chart: `charts/claim4_tatasteel_volume_spike.html`
"""
    Path("claims.md").write_text(claims_md)
    print("\nclaims.md written.")