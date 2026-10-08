import { convertBalanceForDisplay } from './currency-conversion';

describe('convertBalanceForDisplay', () => {
  it('returns original amount when account base currency matches preference currency', () => {
    expect(convertBalanceForDisplay(100, 'USD', 'USD')).toBe(100);
    expect(convertBalanceForDisplay(9600, 'INR', 'INR')).toBe(9600);
  });

  it('converts USD base currency to INR preference currency using static rate', () => {
    expect(convertBalanceForDisplay(100, 'USD', 'INR')).toBe(9600);
  });

  it('converts INR base currency to USD preference currency using static rate', () => {
    expect(convertBalanceForDisplay(9600, 'INR', 'USD')).toBe(100);
  });

  it('rounds converted value to two decimals', () => {
    expect(convertBalanceForDisplay(1000, 'INR', 'USD')).toBe(10.42);
  });
});
