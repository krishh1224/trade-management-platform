const INSTRUMENT_CURRENCY_BY_SYMBOL: Record<string, string> = {
  'INFY.NS': 'INR',
  'RELIANCE.NS': 'INR',
  'TATASTEEL.BO': 'INR',
  'HDFCBANK.NS': 'INR',
  'ICICIBANK.NS': 'INR',
  'ITC.NS': 'INR',
  INFY: 'INR',
  RELIANCE: 'INR',
  TATASTEEL: 'INR',
  HDFCBANK: 'INR',
  ICICIBANK: 'INR',
  ITC: 'INR'
};

export function currencyForSymbol(symbol: string | null | undefined): string {
  const normalized = (symbol ?? '').trim().toUpperCase();
  if (normalized.length === 0) {
    return 'INR';
  }

  const seeded = INSTRUMENT_CURRENCY_BY_SYMBOL[normalized];
  if (seeded !== undefined) {
    return seeded;
  }

  if (normalized.endsWith('.NS') || normalized.endsWith('.BO')) {
    return 'INR';
  }

  if (normalized.startsWith('FX:') && normalized.length >= 9) {
    return normalized.slice(-3);
  }

  return 'INR';
}
