/** Every seeded account holds its cash in INR; the balance endpoint reports the real one. */
export const DEFAULT_ACCOUNT_CURRENCY = 'INR';

/**
 * Currency an instrument is quoted (and its limit price entered) in, derived from the Fauxnance
 * symbol scheme used by INSTRUMENTS.ticker:
 *
 *   INFY.NS, TATASTEEL.BO  -> INR (NSE / BSE)
 *   FX:EURUSD              -> USD (the quote currency, last three letters)
 *   X:BTC-USD              -> USD (the part after the dash)
 *   AAPL, JPM              -> USD (plain tickers are US equities on NASDAQ / NYSE)
 *
 * Prefer the `currency` returned with the instrument list when it is available; this is the
 * fallback for places that only hold a symbol (order history, blotter).
 */
export function currencyForSymbol(symbol: string | null | undefined): string {
  const normalized = (symbol ?? '').trim().toUpperCase();
  if (normalized.length === 0) {
    return DEFAULT_ACCOUNT_CURRENCY;
  }

  if (normalized.endsWith('.NS') || normalized.endsWith('.BO')) {
    return 'INR';
  }

  if (normalized.startsWith('FX:')) {
    return normalized.length >= 9 ? normalized.slice(-3) : DEFAULT_ACCOUNT_CURRENCY;
  }

  if (normalized.startsWith('X:')) {
    const dash = normalized.lastIndexOf('-');
    return dash > 0 && normalized.length - dash - 1 === 3 ? normalized.slice(dash + 1) : 'USD';
  }

  return 'USD';
}

/** Display label used in the UI without changing stored or API currency codes. */
export function currencySymbolForDisplay(currency: string | null | undefined): string {
  const normalized = (currency ?? '').trim().toUpperCase();
  if (normalized === 'INR') {
    return '₹';
  }
  if (normalized === 'USD') {
    return '$';
  }
  return normalized;
}
