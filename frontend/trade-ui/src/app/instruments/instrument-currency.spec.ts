import { currencyForSymbol } from './instrument-currency';

describe('currencyForSymbol', () => {
  it('maps NSE and BSE symbols to INR', () => {
    expect(currencyForSymbol('INFY.NS')).toBe('INR');
    expect(currencyForSymbol('tatasteel.bo')).toBe('INR');
  });

  it('maps plain US tickers (NASDAQ and NYSE) to USD', () => {
    expect(currencyForSymbol('AAPL')).toBe('USD');
    expect(currencyForSymbol('JPM')).toBe('USD');
  });

  it('uses the quote currency of an FX pair', () => {
    expect(currencyForSymbol('FX:EURUSD')).toBe('USD');
    expect(currencyForSymbol('FX:USDINR')).toBe('INR');
    expect(currencyForSymbol('FX:USDJPY')).toBe('JPY');
  });

  it('uses the currency after the dash for crypto', () => {
    expect(currencyForSymbol('X:BTC-USD')).toBe('USD');
  });

  it('falls back to the account currency for an empty symbol', () => {
    expect(currencyForSymbol('')).toBe('INR');
    expect(currencyForSymbol(null)).toBe('INR');
  });
});
