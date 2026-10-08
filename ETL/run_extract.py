"""Run the extract step for all symbols."""
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%H:%M:%S",
)

from analytics_etl.extract import extract

symbols = ["INFY.NS", "RELIANCE.NS", "TATASTEEL.BO", "AAPL", "MSFT", "X:BTC-USD"]
result = extract(symbols, start="2025-08-01", end="2026-08-26")

print("\n=== RESULTS ===")
for sym, r in result.items():
    candles = r["data"]["candles"]
    currency = r["data"]["currency"]
    first = candles[0]["date"] if candles else "N/A"
    last = candles[-1]["date"] if candles else "N/A"
    print(f"  {sym}: {len(candles)} candles ({currency}), {first} -> {last}")

print(f"\nTotal symbols extracted: {len(result)}/{len(symbols)}")

